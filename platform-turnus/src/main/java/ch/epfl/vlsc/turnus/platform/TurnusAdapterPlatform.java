package ch.epfl.vlsc.turnus.platform;

import ch.epfl.vlsc.phases.VerilogNameCheckerPhase;
import ch.epfl.vlsc.turnus.phase.TurnusAdapterPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class TurnusAdapterPlatform implements Platform {
    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.templatePhases())
            .addAll(Compiler.networkElaborationPhases())
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(prepareActorPhases())
            .add(new VerilogNameCheckerPhase())
            .add(new TurnusAdapterPhase())
            .build();

    public static List<Phase> prepareActorPhases() {
        return ImmutableList.of(
                new ActionCaseToActionsPhase(),
                new RenameActorVariablesPhase(),
                new LiftProcessVarDeclsPhase(),
                new ProcessToCalPhase(),
                new AddSchedulePhase(),
                new ScheduleUntaggedPhase(),
                new ScheduleInitializersPhase(),
                new AddPrioritiesPhase(),
                new RemoveIdleMatchExpressionsPhase(),
                new SubstitutePatternBindingsPhase(),
                new AddMatchGuardsPhase(),
                new MergeManyGuardsPhase()
        );
    }

    @Override
    public String name() {
        return "turnus";
    }

    @Override
    public String description() {
        return "Classifies every actor in a Dataflow Network";
    }

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
