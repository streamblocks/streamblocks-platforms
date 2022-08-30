package ch.epfl.vlsc.raft.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.raft.backend.RaftBackend;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.AliasType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.MapType;
import se.lth.cs.tycho.type.SetType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.TupleType;
import se.lth.cs.tycho.type.Type;

@Module
public interface Free {

    @Binding(BindingKind.INJECTED)
    RaftBackend backend();

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default Emitter emitter() {
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
