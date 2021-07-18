package ch.epfl.vlsc.wsim.phase;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.phase.Phase;
import org.multij.Module;

public class ActorMachineToCppClassConversionPhase implements Phase {

    @Override
    public String getDescription() { return "Transform actor machines into C++ classes"; }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        return task;
    }

    @Module
    interface Transformation extends IRNode.Transformation {


        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default

    }
}
