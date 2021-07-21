package ch.epfl.vlsc.wsim.phase;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PropagatePortTypeToVarDeclUsedOnPorts implements Phase {
    @Override
    public String getDescription() {
        return "Propagate port type to variable declaration that used for reading or writing";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        return task;
    }

    @Module
    interface Transformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        Types types();

        @Binding(BindingKind.LAZY)
        default Map<VarDecl, TypeExpr> declMap() {
            return new HashMap<>();
        }

        default void checkAndCollect(IRNode node) {
            node.forEachChild(this::checkAndCollect);
        }
        default void checkAndCollect(StmtWrite writeOperation) {

            if (writeOperation.getRepeatExpression() == null) {

            }
        }

    }
}
