package ch.epfl.vlsc.sw.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.*;

import java.util.stream.Collectors;

@Module
public interface Declarations {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    default String declaration(Type type, String name) {
        return backend().typeseval().type(type) + " " + name;
    }

    default String declaration(UnitType type, String name) {
        return "char " + name;
    }

    /*
    default String declaration(ListType type, String name) {
        return String.format("%s %s%s", backend().typeseval().type(type), name, getListDims(type));
    }*/

    default String declaration(ListType type, String name) {
        if (type.getSize().isPresent()) {
            String maxIndex = backend().typeseval().sizeByDimension(type).stream().map(Object::toString).collect(Collectors.joining("*"));
            return String.format("%s %s[%s]", backend().typeseval().type(type), name, maxIndex);
        } else {
            return String.format("%s %s[]", backend().typeseval().type(type), name);
        }
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

    default String declaration(TupleType type, String name){
        String tupleTypes = type.getTypes().stream().sequential().map(e -> backend().typeseval().type(e)).collect(Collectors.joining(", "));
        String ret = String.format("std::tuple< %s > %s", tupleTypes, name);
        return ret;
    }

    default String persistentDeclaration(Type type, String name) {
        if(!backend().typeseval().isScalar(type)){
            return backend().typeseval().type(type) + " *" + name;
        }else {
            return declaration(type, name);
        }
    }

    default String getPointerDims(ListType type){
        if(type.getElementType() instanceof ListType){
            return String.format("*%s", getPointerDims((ListType)type.getElementType()));
        }else{
            return "*";
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
        return "bool " + name;
    }

    default String declaration(StringType type, String name) {
        return "std::string " + name;
    }

    default String declarationTemp(Type type, String name) {
        return declaration(type, name);
    }

    default String declarationTemp(ListType type, String name) {
        if (type.getSize().isPresent()) {
            String maxIndex = backend().typeseval().sizeByDimension(type).stream().map(Object::toString).collect(Collectors.joining("*"));
            return String.format("%s %s[%s]", backend().typeseval().type(type), name, maxIndex);
        } else {
            return String.format("%s %s[]", backend().typeseval().type(type), name);
        }
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
