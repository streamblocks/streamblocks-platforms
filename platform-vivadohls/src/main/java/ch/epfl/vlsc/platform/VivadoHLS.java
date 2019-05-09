package ch.epfl.vlsc.platform;

import ch.epfl.vlsc.phase.VivadoHLSBackendPhase;
import ch.epfl.vlsc.phases.AddFanoutPhase;
import ch.epfl.vlsc.phases.CalToAmHwPhase;
import ch.epfl.vlsc.phases.LiftExprInputFromScopesPhase;
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



    public static List<Phase> networkElaborationPhases() {
        return ImmutableList.of(
                new CreateNetworkPhase(),
                new RenameActorVariablesPhase(),
                new ResolveGlobalEntityNamesPhase(),
                new ResolveGlobalVariableNamesPhase(),
                new ElaborateNetworkPhase(),
                new AddFanoutPhase(),
                new ResolveGlobalEntityNamesPhase(),
                new RemoveUnusedGlobalDeclarations()
        );
    }


    public static List<Phase> actorMachinePhases() {
        return ImmutableList.of(
                new LiftProcessVarDeclsPhase(),
                new AddSchedulePhase(),
                new ScheduleUntaggedPhase(),
                new ScheduleInitializersPhase(),
                new MergeManyGuardsPhase(),
                new CalToAmHwPhase(),
                new RemoveEmptyTransitionsPhase(),
                new ReduceActorMachinePhase(),
                new CompositionEntitiesUniquePhase(),
                new CompositionPhase(),
                new InternalizeBuffersPhase(),
                new RemoveUnusedConditionsPhase(),
                new LiftExprInputFromScopesPhase(),
                new LiftScopesPhase()
        );
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(networkElaborationPhases())
            .addAll(actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new VivadoHLSBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
