package ch.epfl.vlsc.phases;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

public class ExtractHardwarePartition implements Phase {
    @Override
    public String getDescription() {
        return "Extract the hardware partition from a partitioned network";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,
                "ExtractHardwarePartition phase not implemented"));
    }

}
