package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

@Module
public interface Allocate {

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

    default void apply(ListType type, String pointer) {
        emitter().emit("%s = (%s %s) calloc(%s, sizeof(%s));", pointer, backend().typeseval().type(type), backend().declarations().getPointerDims(type), type.getSize().getAsLong(), backend().typeseval().pointerType(type));

        if (type.getElementType() instanceof ListType) {
            ListType lt = (ListType) type.getElementType();
            allocate(lt, pointer, lt.getSize().getAsLong(), "");
        }
    }

    default void allocate(ListType type, String pointer, long size,  String index) {
        String temp = backend().variables().generateTemp();
        emitter().emit("for(int %1$s = 0; %1$s < %2$s; %1$s++){", temp,  size);
        emitter().increaseIndentation();

        String dim = String.format("%s[%s]", index,  temp);

        emitter().emit("%s%s = (%s %s) calloc(%s, sizeof(%s));", pointer, dim,
                backend().typeseval().type(type),
                backend().declarations().getPointerDims(type),
                type.getSize().getAsLong(),
                backend().typeseval().pointerType(type));


        if (type.getElementType() instanceof ListType) {
            ListType lt = (ListType) type.getElementType();
            allocate(lt, pointer, lt.getSize().getAsLong(), dim);
        }

        emitter().decreaseIndentation();
        emitter().emit("}");
    }

}
