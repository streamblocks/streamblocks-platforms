package ch.epfl.vlsc.cpp.backend.emitters;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.type.*;
import ch.epfl.vlsc.cpp.backend.CppBackend;

import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface Declarations {

    @Binding(BindingKind.INJECTED)
    CppBackend backend();

    @Binding(BindingKind.INJECTED)
    Types types();


    default TypesEvaluator typeseval(){
        return backend().typeseval();
    }


    default String declaration(Type type, String name) {
        return typeseval().type(type) + " " + name;
    }

    default String declaration(TupleType type, String name){
        String tupleTypes = type.getTypes().stream().sequential().map(e -> backend().typeseval().type(e)).collect(Collectors.joining(", "));
        String ret = String.format("std::tuple< %s > %s", tupleTypes, name);
        return ret;
    }

    default String declaration(UnitType type, String name) {
        return "char " + name;
    }

    default String declaration(RefType type, String name) {
        return declaration(type.getType(), String.format("(*%s)", name));
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

    default String declaration(ListType type, String name) {
        return String.format("%s %s", backend().typeseval().type(type), name);
    }

    default String getListDims(ListType type){
        if(type.getElementType() instanceof ListType){
            return String.format("[%s]%s", type.getSize().getAsLong(), getListDims((ListType)type.getElementType()));
        }else{
            return String.format("[%s]", type.getSize().getAsLong());
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


    default String portInputDeclaration(String instanceName, PortDecl portDecl, String prefix) {
        Connection.End target = new Connection.End(Optional.of(instanceName), portDecl.getName());
        int size = backend().channels().targetEndSize(target);
        Type type = types().declaredPortType(portDecl);
        return String.format("Port< %s, %d > &%s$FIFO", declaration(type, ""), size, prefix + portDecl.getName());
    }

    default String portOutputDeclaration(String instanceName, PortDecl portDecl, String prefix) {
        Connection.End source = new Connection.End(Optional.of(instanceName), portDecl.getName());
        int size = backend().channels().sourceEndSize(source);
        Type type = types().declaredPortType(portDecl);
        return String.format("Port< %s, %d > &%s$FIFO", declaration(type, ""), size, prefix + portDecl.getName());
    }


}
