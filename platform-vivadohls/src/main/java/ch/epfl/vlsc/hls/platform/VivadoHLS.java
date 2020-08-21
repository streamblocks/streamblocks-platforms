package ch.epfl.vlsc.hls.platform;

import ch.epfl.vlsc.hls.phase.VivadoHLSBackendPhase;
import ch.epfl.vlsc.phases.*;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class VivadoHLS implements Platform {
    @Override
    public String name() {
        return "vivado-hls";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for VivadoHLS.";
    }


    public static List<Phase> postPartitionNetworkElaborationPhases() {
        return ImmutableList.of(
                new ResolveGlobalEntityNamesPhase(),
                new AddFanoutPhase(),
                new ResolveGlobalEntityNamesPhase(),
                new RemoveUnusedGlobalDeclarations(),
                new VerilogNameCheckerPhase()
        );
    }
    public static List<Phase> actorMachinePhases() {
        return ImmutableList.of(
                new RenameActorVariablesPhase(),
                new LiftProcessVarDeclsPhase(),
                new ProcessToCalPhase(),
                new RemoveIdleMatchExpressionsPhase(),
                new SubstitutePatternBindingsPhase(),
                new AddMatchGuardsPhase(),
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


    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.templatePhases())
            .add(new RecursiveTypeDetectionPhase())
            .addAll(CommonPhases.networkElaborationPhases)
            .add(new VerilogNameCheckerPhase())
            .addAll(Compiler.nameAndTypeAnalysis())
//            .add(new XcfAnnotationPhase())
            .addAll(CommonPhases.hardwarePartitioningPhases)
            .addAll(postPartitionNetworkElaborationPhases())
            .addAll(actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new VivadoHLSBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
