package ch.epfl.vlsc.raft.backend.emitters;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.RefType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.Type;
import se.lth.cs.tycho.type.UnitType;
import ch.epfl.vlsc.raft.backend.RaftBackend;

import java.util.Optional;

@Module
public interface Declarations {

    @Binding(BindingKind.INJECTED)
    RaftBackend backend();

    @Binding(BindingKind.INJECTED)
    Types types();


    default TypesEvaluator typeseval(){
        return backend().typeseval();
    }


    default String declaration(Type type, String name) {
        return typeseval().type(type) + " " + name;
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
        return String.format("%s %s%s", backend().typeseval().type(type), name, getListDims(type));
    }

    default String getListDims(ListType type){
        if(type.getElementType() instanceof ListType){
            return String.format("[%s]%s", type.getSize().getAsInt(), getListDims((ListType)type.getElementType()));
        }else{
            return String.format("[%s]", type.getSize().getAsInt());
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
