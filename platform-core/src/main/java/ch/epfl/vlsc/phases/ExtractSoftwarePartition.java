package ch.epfl.vlsc.phases;

import ch.epfl.vlsc.compiler.PartitionedCompilationTask;
import ch.epfl.vlsc.settings.PlatformSettings;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import ch.epfl.vlsc.compiler.PartitionedCompilationTask.PartitionKind;

/**
 * @author Mahyar Emami (mahyar.emami@epfl.ch)
 * @brief Extract the software partition from a partitioned compilation task
 */
public class ExtractSoftwarePartition implements Phase {
    @Override
    public String getDescription() {
        return "Extracts the software partition from a partitioned network";
    }

    @Override
    public Set<Class<? extends Phase>> dependencies() {
        Set<Class<? extends Phase>> deps = new HashSet<>();
        deps.add(NetworkPartitioningPhase.class);
        return deps;
    }
    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Boolean paritioningEnabled = context.getConfiguration().isDefined(PlatformSettings.PartitionNetwork)
                && context.getConfiguration().get(PlatformSettings.PartitionNetwork);
        context.getReporter().report(
                new Diagnostic(Diagnostic.Kind.INFO, "Extracting " + PartitionKind.SW.toString() +" partition")
        );
        if (paritioningEnabled && task instanceof PartitionedCompilationTask) {

            PartitionedCompilationTask ptask = (PartitionedCompilationTask) task;
            return ptask.extractPartition(context, PartitionKind.SW);

        } else {
            if (paritioningEnabled)
                throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, "In phase ExtractSoftwarePartition, " +
                                "the CompilationTask is not and instance of PartitionedCompilationTask, make " +
                                "sure the NetworkPartitioningPhase is enabled."));
            else
                context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Skipping ExtractSoftwarePartitionPhase " +
                            " because partitioning is disabled."));
            return task;
        }

    }
}
