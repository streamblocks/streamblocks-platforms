package ch.epfl.vlsc.raft.backend.emitters;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Closures;
import se.lth.cs.tycho.attribute.VariableDeclarations;
import se.lth.cs.tycho.compiler.UniqueNumbers;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.expr.ExprGlobalVariable;
import se.lth.cs.tycho.phase.TreeShadow;

import java.util.stream.Collectors;

@Module
public interface Variables {

    @Binding(BindingKind.INJECTED)
    UniqueNumbers uniqueNumbers();

    @Binding(BindingKind.INJECTED)
    TreeShadow tree();

    @Binding(BindingKind.INJECTED)
    VariableDeclarations varDecls();

    @Binding(BindingKind.INJECTED)
    Closures closures();

    /**
     * Generate a temporary variable
     *
     * @return
     */
    default String generateTemp() {
        return "t_" + uniqueNumbers().next();
    }


    /**
     * Variables in declarations
     *
     * @param decl
     * @return
     */

    default String declarationName(VarDecl decl) {
        IRNode parent = tree().parent(decl);
        if (parent instanceof NamespaceDecl || parent instanceof ActorMachine || parent instanceof Scope) {
            return decl.getName();
        } else {
            return "l_" + decl.getName();
        }
    }

    /**
     * Variables as globals
     *
     * @param var
     * @return
     */

    default String globalName(ExprGlobalVariable var) {
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
        IRNode parent = tree().parent(decl);
        if (parent instanceof Scope || parent instanceof ActorMachine || parent instanceof CalActor) {
            return "&(this->" + declarationName(decl) + ")";
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
        VarDecl decl = varDecls().declaration(var);
        IRNode parent = tree().parent(decl);
        if (closures().isDeclaredInClosure(var)) {
            return "this->" + declarationName(decl);
        } else if (parent instanceof Scope || parent instanceof ActorMachine || parent instanceof CalActor) {
            return "this->" + declarationName(decl);
        } else {
            return declarationName(decl);
        }
    }

    default String name(VarDecl decl) {
        IRNode parent = tree().parent(decl);
        if (parent instanceof Scope || parent instanceof ActorMachine || parent instanceof CalActor) {
            return "this->" + declarationName(decl) + "";
        } else {
            return declarationName(decl);
        }
    }
}
