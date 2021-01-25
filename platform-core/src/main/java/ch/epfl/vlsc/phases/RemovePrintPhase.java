package ch.epfl.vlsc.phases;

import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.stmt.StmtBlock;
import se.lth.cs.tycho.ir.stmt.StmtCall;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

public class RemovePrintPhase implements Phase {
    @Override
    public String getDescription() {
        return "Removes print and println calls";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Transformation transformation = MultiJ.from(Transformation.class)
                .instance();

        return task.transformChildren(transformation);
    }


    @Module
    interface Transformation extends IRNode.Transformation {
        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }


        default IRNode apply(StmtCall call) {
            if (call.getProcedure() instanceof ExprVariable) {
                ExprVariable var = (ExprVariable) call.getProcedure();
                String name = var.getVariable().getName();
                if (name.equals("print") || name.equals("println")) {
                    return new StmtBlock(ImmutableList.empty(), ImmutableList.empty(), ImmutableList.empty());
                }
            }
            return call;
        }
    }
}