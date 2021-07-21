package ch.epfl.vlsc.wsim.phase;

import ch.epfl.vlsc.wsim.ir.cpp.CppNominalTypeExpr;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.function.Consumer;

public class CppNominalTypeAssertionPhase implements Phase {

    @Override
    public String getDescription() {
        return "Assert that all type expression have been converted to CppNominalType";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        TypeChecker checker = MultiJ.from(TypeChecker.class)
                .bind("tree").to(task.getModule(TreeShadow.key)).instance();
        try{
            task.forEachChild(checker);
        } catch (CompilationException e) {
            context.getReporter().report(
                    new Diagnostic(
                            Diagnostic.Kind.ERROR, "Failed to validate types: \n" + e.getMessage()
                    )
            );
        }

        return task;
    }

    @Module
    interface TypeChecker extends Consumer<IRNode> {

        @Binding(BindingKind.INJECTED)
        TreeShadow tree();

        default void accept(IRNode node) {
            node.forEachChild(this);
        }

        default void accept(TypeExpr typeExpr) {
            IRNode parent = tree().parent(typeExpr);
            if (!(typeExpr instanceof CppNominalTypeExpr))
                throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "Type check failed, not all types are C++ compatible",
                                findSourceUnit(typeExpr), tree().parent(typeExpr))
                );
        }

        default SourceUnit findSourceUnit(IRNode node) {
            return findSourceUnit(tree().parent(node));
        }
        default SourceUnit findSourceUnit(SourceUnit node) {
            return node;
        }


    }
}
