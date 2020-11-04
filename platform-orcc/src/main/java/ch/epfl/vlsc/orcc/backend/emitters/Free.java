package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.*;

@Module
public interface Free {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default Emitter
    emitter() {
        return backend().emitter();
    }

    default void apply(Type type, String pointer) {

    }

    default void apply(AlgebraicType type, String pointer) {
        emitter().emit("%s(%s);", backend().algebraic().utils().destructor(type), pointer);
    }

    default void apply(SetType type, String pointer) {
        emitter().emit("free_%s(%s);", typeseval().type(type), pointer);
    }

    default void apply(MapType type, String pointer) {
        emitter().emit("free_%s(%s);", typeseval().type(type), pointer);
    }

    default void apply(TupleType type, String pointer) {
        apply(backend().tuples().convert().apply(type), pointer);
    }

    default void apply(StringType type, String pointer) {
        emitter().emit("free_%s(%s);", typeseval().type(type), pointer);
    }

    default void apply(ListType type, String pointer) {
        emitter().emit("for (size_t i = 0; i < %s; ++i) {", type.getSize().getAsInt());
        emitter().increaseIndentation();
        apply(type.getElementType(), String.format("%s.data[i]", pointer));
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void apply(AliasType type, String pointer) {
        apply(type.getConcreteType(), pointer);
    }
}
