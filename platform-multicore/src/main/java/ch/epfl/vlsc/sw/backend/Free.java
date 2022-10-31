package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.*;

@Module
public interface Free {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

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

    default void apply(StringType type, String pointer) {
        emitter().emit("free_%s(%s);", typeseval().type(type), pointer);
    }

    default void apply(ListType type, String pointer) {
        if (type.getElementType() instanceof ListType) {
            free((ListType) type.getElementType(), pointer, "");
        }

        emitter().emit("free(%s);", pointer);
    }

    default void free(ListType type, String pointer, String index) {
        String temp = backend().variables().generateTemp();
        String dim = String.format("%s[%s]", index, temp);

        emitter().emit("for(int %1$s = 0; %1$s < %2$s; %1$s++){", temp, type.getSize().getAsInt());
        emitter().increaseIndentation();

        if(type.getElementType() instanceof ListType){
            ListType lt = (ListType) type.getElementType();
            free(lt, pointer, dim);
        }

        emitter().emit("free(%s%s);", pointer, dim);

        emitter().decreaseIndentation();
        emitter().emit("}");

    }

    default void apply(AliasType type, String pointer) {
        apply(type.getConcreteType(), pointer);
    }
}
