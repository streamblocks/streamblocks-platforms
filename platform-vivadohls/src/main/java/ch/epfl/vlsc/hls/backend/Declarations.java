package ch.epfl.vlsc.hls.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.type.*;

import java.util.stream.Collectors;

@Module
public interface Declarations {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }


    default String declaration(Type type, String name) {
        return typeseval().type(type) + " " + name;
    }

    default String declaration(UnitType type, String name) {
        return "char " + name;
    }

    default String declaration(ListType type, String name) {
        String maxIndex = typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
        return String.format("%s %s[%s]", typeseval().type(type), name, maxIndex);
    }

    default String declaration(RefType type, String name) {
        return declaration(type.getType(), String.format("(*%s)", name));
    }


    default String declaration(BoolType type, String name) {
        return "bool " + name;
    }

    default String declaration(StringType type, String name) {
        return "char * " + name;
    }

    default String declarationTemp(Type type, String name) {
        return declaration(type, name);
    }

    default String declarationTemp(ListType type, String name) {
        String maxIndex = typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
        return String.format("%s %s[%s]", typeseval().type(type), name, maxIndex);
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


    default String portDeclaration(PortDecl portDecl){
        Type type = backend().types().declaredPortType(portDecl);
        return String.format("hls::stream< %s> &%s", declaration(type, ""), portDecl.getName());
    }

}
