package ch.epfl.vlsc.sw.phase;

import ch.epfl.vlsc.phases.HardwarePartitioningPhase;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.settings.Setting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreatePartitionLinkPhase implements Phase {


    @Override
    public String getDescription() {
        return "Creates a PartitionLink Entity that and directs all floating connections to a instance of this entity";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "PartitionLinkPhase not implemented"));
        //return task;
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return null;
    }

    @Override
    public Set<Class<? extends Phase>> dependencies() {
        Set<Class<? extends Phase>> deps = new HashSet<>();
        deps.add(HardwarePartitioningPhase.class);
        return deps;
    }
}
