package ch.epfl.vlsc.sw.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.expr.ExprGlobalVariable;
//import se.lth.cs.tycho.type.TensorType;
import se.lth.cs.tycho.type.Type;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Module
public interface Variables {
    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    /**
     * Generate a temporary variable
     *
     * @return
     */
    default String generateTemp() {
        return "t_" + backend().uniqueNumbers().next();
    }

    /**
     * Escape in variables names
     *
     * @param name
     * @return
     */

    default String escape(String name) {
        return name.replace("_", "__");
    }

    /**
     * Variables in declarations
     *
     * @param decl
     * @return
     */

    default String declarationName(VarDecl decl) {
        IRNode parent = backend().tree().parent(decl);
        if (parent instanceof Scope) {
            return "a_" + escape(decl.getName());
        } else if (parent instanceof ActorMachine) {
            return "a_" + escape(decl.getName());
        } else if (parent instanceof NamespaceDecl) {
            return decl.getName();
        } else {
            return "l_" + escape(decl.getName());
        }
    }

    /**
     * Variables as globals
     *
     * @param var
     * @return
     */

    default String globalName(ExprGlobalVariable var) {
        /*
        return var.getGlobalName().parts().stream()
                .map(this::escape)
                .collect(Collectors.joining("_", "g_", ""));
                */
        return var.getGlobalName().parts().stream()
                .collect(Collectors.joining("::", "", ""));
    }

    /**
     * Variables as reference
     *
     * @param decl
     * @return
     */

    default String reference(VarDecl decl) {
        IRNode parent = backend().tree().parent(decl);
        if (parent instanceof Scope || parent instanceof ActorMachine) {
            return "&(thisActor->" + declarationName(decl) + ")";
        } else {
            return "&" + declarationName(decl);
        }
    }

    /**
     * Variables in closures and scopes
     *
     * @param var
     * @return
     */
    default String name(Variable var) {
        VarDecl decl = backend().varDecls().declaration(var);
        IRNode parent = backend().tree().parent(decl);
        if (parent instanceof Scope || parent instanceof ActorMachine) {
            Type type = backend().types().type(decl.getType());
//            if(type instanceof TensorType){
//                return "*thisActor->" + declarationName(decl);
//            }else{
                return "thisActor->" + declarationName(decl);
//            }
        } else {
            return declarationName(decl);
        }
    }

    default String name(VarDecl decl) {
        IRNode parent = backend().tree().parent(decl);
        if (parent instanceof Scope || parent instanceof ActorMachine) {
            return "thisActor->" + declarationName(decl) + "";
        } else {
            return declarationName(decl);
        }
    }

}
