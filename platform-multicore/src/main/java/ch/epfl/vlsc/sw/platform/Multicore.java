package ch.epfl.vlsc.sw.platform;

import ch.epfl.vlsc.phases.AnnotateExternalMemories;
import ch.epfl.vlsc.phases.ExtractSoftwarePartition;
import ch.epfl.vlsc.phases.NetworkPartitioningPhase;
import ch.epfl.vlsc.phases.VerilogNameCheckerPhase;
import ch.epfl.vlsc.sw.phase.CreatePartitionLinkPhase;
import ch.epfl.vlsc.sw.phase.MultiCoreBackendPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class Multicore implements Platform {
    @Override
    public String name() {
        return "multicore";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for multicore platforms that uses PThread.";
    }

    public static List<Phase> actorMachinePhases() {
        return ImmutableList.of(
                new LiftProcessVarDeclsPhase(),
                new AddSchedulePhase(),
                new ScheduleUntaggedPhase(),
                new ScheduleInitializersPhase(),
                new MergeManyGuardsPhase(),
                new CalToAmPhase(),
                new RemoveEmptyTransitionsPhase(),
                new ReduceActorMachinePhase(),
                new CompositionEntitiesUniquePhase(),
                new CompositionPhase(),
                new InternalizeBuffersPhase(),
                new RemoveUnusedConditionsPhase(),
                new LiftScopesPhase()
        );
    }

    public static List<Phase> networkElaborationPhases() {
        return ImmutableList.of(
                new CreateNetworkPhase(),
                new ResolveGlobalEntityNamesPhase(),
                new ResolveGlobalVariableNamesPhase(),
                new ElaborateNetworkPhase(),
                new RemoveUnusedGlobalDeclarations()
        );
    }

    public static List<Phase> partitioningPhases() {
        return ImmutableList.of(
                new VerilogNameCheckerPhase(),
                new NetworkPartitioningPhase(),
                new AnnotateExternalMemories(),
                new ExtractSoftwarePartition()
//                new CreatePartitionLinkPhase()
        );
    }
    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.networkElaborationPhases())
            .addAll(partitioningPhases())
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(Compiler.actorMachinePhases())
//            .add(new RemoveUnusedEntityDeclsPhase()) // This can not happen after network elaboration because the hardware partition entities get removed.
            .add(new CreatePartitionLinkPhase())
            .add(new MultiCoreBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
