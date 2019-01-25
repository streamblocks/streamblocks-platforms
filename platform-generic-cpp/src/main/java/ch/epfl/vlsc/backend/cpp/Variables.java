package ch.epfl.vlsc.backend.cpp;

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
import se.lth.cs.tycho.ir.type.FunctionTypeExpr;
import se.lth.cs.tycho.ir.type.ProcedureTypeExpr;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Module
public interface Variables {
    @Binding(BindingKind.INJECTED)
    Backend backend();

    default String generateTemp() {
        return "t_" + backend().uniqueNumbers().next();
    }

    default String escape(String name) {
        return name.replace("_", "__");
    }

    default String declarationName(VarDecl decl) {
        IRNode parent = backend().tree().parent(decl);
        if (parent instanceof Scope) {
            if ((decl.getType() instanceof ProcedureTypeExpr) || (decl.getType() instanceof FunctionTypeExpr)) {
                return "f_" + decl.getName();
            } else {
                return "a_" + escape(decl.getName());
            }
        } else if (parent instanceof ActorMachine) {
            return "a_" + escape(decl.getName());
        } else if (parent instanceof NamespaceDecl) {
            return decl.getName();
        } else {
            return "l_" + escape(decl.getName());
        }
    }

    default String globalName(ExprGlobalVariable var) {
        return var.getGlobalName().parts().stream()
                .collect(Collectors.joining("::", "", ""));
    }

    default String reference(VarDecl decl) {
        IRNode parent = backend().tree().parent(decl);
        if (parent instanceof Scope || parent instanceof ActorMachine) {
            return "&(this->" + declarationName(decl) + ")";
        } else {
            return "&" + declarationName(decl);
        }
    }

    default String name(Variable var) {
        VarDecl decl = backend().varDecls().declaration(var);
        IRNode parent = backend().tree().parent(decl);
        if (backend().closures().isDeclaredInClosure(var)) {
            return declarationName(decl);
        } else if (parent instanceof Scope || parent instanceof ActorMachine) {
            if (decl.getType() instanceof ProcedureTypeExpr) {
                return "this->" + declarationName(decl);
            } else {
                return "(this->" + declarationName(decl) + ")";
            }
        } else {
            return declarationName(decl);
        }
    }
}