package ch.epfl.vlsc.mlir.phase;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

public class MlirPhase implements Phase {
    @Override
    public String getDescription() {
        return "A phase dedicated to MLIR stuff";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        return null;
    }
}
