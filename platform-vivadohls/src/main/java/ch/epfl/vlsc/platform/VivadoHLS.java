package ch.epfl.vlsc.platform;

import ch.epfl.vlsc.phase.VivadoHLSBackendPhase;
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
        return "A backend for Vivado HLS.";
    }


    public static List<Phase> actorMachinePhases() {
        return ImmutableList.of(
                new RenameActorVariablesPhase()
                //new LiftProcessVarDeclsPhase()
                //new AddSchedulePhase(),
                //new ScheduleUntaggedPhase(),
                //new ScheduleInitializersPhase(),
                //new MergeManyGuardsPhase(),
                //new CalToAmPhase(),
                //new RemoveEmptyTransitionsPhase(),
                //new ReduceActorMachinePhase(),
                //new CompositionEntitiesUniquePhase(),
                //new CompositionPhase(),
                //new InternalizeBuffersPhase(),
                //new RemoveUnusedConditionsPhase(),
                //new LiftScopesPhase()
        );
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.networkElaborationPhases())
            .addAll(actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new VivadoHLSBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
