package ch.epfl.vlsc.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.type.*;

import java.util.stream.Collectors;

@Module
public interface Declarations {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default String declaration(Type type, String name) {
        return backend().typeseval().type(type) + " " + name;
    }

    default String declaration(UnitType type, String name) {
        return "char " + name;
    }

    default String declaration(ListType type, String name) {
        return backend().typeseval().type(type) + "* " + name;
    }

    default String declaration(RefType type, String name) {
        //if(type.getType() instanceof ListType){
        //    return declaration(type.getType(), String.format("(%s)", name));
        //}else{
        return declaration(type.getType(), String.format("(*%s)", name));
        //}

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

    default String declarationTemp(ListType type, String name) {
        String maxIndex = backend().typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
        return String.format("%s %s[%s]", backend().typeseval().type(type), name, maxIndex);
    }

}
