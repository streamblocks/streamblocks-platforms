package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.*;

@Module
public interface Declarations {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default String declaration(Type type, String name) {
        return backend().typeseval().type(type) + " " + name;
    }

    default String declaration(UnitType type, String name) {
        return "char " + name;
    }

    default String declaration(ListType type, String name) {
        return String.format("%s %s%s", backend().typeseval().type(type), name, getListDims(type));
    }

    default String getListDims(ListType type){
        if(type.getElementType() instanceof ListType){
            return String.format("[%s]%s", type.getSize().getAsLong(), getListDims((ListType)type.getElementType()));
        }else{
            if(type.getSize().isPresent()){
                return String.format("[%s]", type.getSize().getAsLong());
            }else{
                return String.format("[]");
            }
        }
    }

    default String declaration(RefType type, String name) {
        return declaration(type.getType(), String.format("(*%s)", name));
    }

    default String declaration(LambdaType type, String name) {
        String t = backend().callables().mangle(type).encode();
        return t + " " + name;
    }

    default String declaration(ProcType type, String name) {
        String t = backend().callables().mangle(type).encode();
        return t + " " + name;
    }

    default String declaration(BoolType type, String name) {
        return "_Bool " + name;
    }

    default String declaration(StringType type, String name) {
        return "char *" + name;
    }

    default String declarationTemp(Type type, String name) {
        return declaration(type, name);
    }



    /*
     * Declaration for parameters
     */

    default String declarationParameter(Type type, String name) {
        return declaration(type, name);
    }

    default String declarationParameter(RefType type, String name) {
        if (type.getType() instanceof ListType) {
            return declaration(type.getType(), String.format("%s", name));
        } else {
            return declaration(type.getType(), String.format("(*%s)", name));
        }
    }
}
