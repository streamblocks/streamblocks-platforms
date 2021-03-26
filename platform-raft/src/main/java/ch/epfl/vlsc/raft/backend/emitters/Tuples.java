package ch.epfl.vlsc.raft.backend.emitters;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.type.FieldType;
import se.lth.cs.tycho.type.ProductType;
import se.lth.cs.tycho.type.TupleType;
import se.lth.cs.tycho.type.Type;
import ch.epfl.vlsc.raft.backend.RaftBackend;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.multij.BindingKind.LAZY;

@Module
public interface Tuples {

    @Binding(BindingKind.INJECTED)
    RaftBackend backend();

    @Binding(LAZY)
    default Convert convert() {
        return MultiJ.from(Convert.class)
                .bind("backend").to(backend())
                .instance();
    }

    @Binding(LAZY)
    default Utils utils() {
        return MultiJ.from(Utils.class)
                .bind("backend").to(backend())
                .bind("convert").to(convert())
                .instance();
    }

    default void forwardTuple() {
        backend().emitter().emit("// -- FORWARD TUPLE DECLARATIONS");
        utils().types().map(convert()::apply).forEach(type -> {
            backend().algebraic().forward().apply(type);
        });
    }

    default void declareTuple() {
        backend().emitter().emit("// -- TUPLE DECLARATIONS");
        utils().types().map(convert()::apply).forEach(type -> {
            backend().algebraic().prototypes().apply(type);
        });
    }

    default void defineTuple() {
        backend().emitter().emit("// -- TUPLE DEFINITIONS");
        utils().types().map(convert()::apply).forEach(type -> {
            backend().algebraic().definitions().apply(type);
        });
    }

    @Module
    interface Convert {

        @Binding(BindingKind.INJECTED)
        RaftBackend backend();

        default ProductType apply(TupleType tuple) {
            return new ProductType(name(tuple), IntStream.range(0, tuple.getTypes().size())
                    .mapToObj(index -> field(index, tuple.getTypes().get(index)))
                    .collect(Collectors.toList()));
        }

        default String name(TupleType type) {
            return type.getTypes().stream()
                    .map(backend().typeseval()::type)
                    .collect(Collectors.joining("_", String.format("Tuple%d_", type.getTypes().size()), ""));
        }

        default FieldType field(Integer index, Type type) {
            return new FieldType("_" + (index + 1), type);
        }
    }

    @Module
    interface Utils {

        @Binding(BindingKind.INJECTED)
        RaftBackend backend();
        @Binding(BindingKind.INJECTED)
        Convert convert();

        default String name(TupleType type) {
            return backend().algebraic().utils().name(convert().apply(type));
        }

        default String constructor(TupleType type) {
            return "init_" + name(type);
        }

        default String destructor(TupleType type) {
            return backend().algebraic().utils().destructor(convert().apply(type));
        }

        default Stream<TupleType> types() {
            return backend().tupleAnnotations().annotations().stream()
                    .map(expr -> (TupleType) backend().types().type(expr))
                    .distinct();
        }
    }
}
