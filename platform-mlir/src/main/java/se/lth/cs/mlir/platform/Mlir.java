package se.lth.cs.mlir.platform;

import java.util.List;

import ch.epfl.vlsc.phases.CommonPhases;
import ch.epfl.vlsc.phases.RemoveSchedulePhase;
import se.lth.cs.mlir.phase.MlirPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.ActionCaseToActionsPhase;
import se.lth.cs.tycho.phase.AddMatchGuardsPhase;
import se.lth.cs.tycho.phase.AddPrioritiesPhase;
import se.lth.cs.tycho.phase.AddSchedulePhase;
import se.lth.cs.tycho.phase.LiftProcessVarDeclsPhase;
import se.lth.cs.tycho.phase.MergeManyGuardsPhase;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.ProcessToCalPhase;
import se.lth.cs.tycho.phase.RemoveIdleMatchExpressionsPhase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.phase.RenameActorVariablesPhase;
import se.lth.cs.tycho.phase.ScheduleInitializersPhase;
import se.lth.cs.tycho.phase.ScheduleUntaggedPhase;
import se.lth.cs.tycho.phase.SubstitutePatternBindingsPhase;
import se.lth.cs.tycho.platform.Platform;

public class Mlir implements Platform {

    @Override
    public String name() {
        return "mlir";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for Mlir runtime.";
    }

public static List<Phase> schedulePhases() {
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
                new MergeManyGuardsPhase(),
                new RemoveSchedulePhase()
        );
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.templatePhases())
            .addAll(CommonPhases.portEnumerationPhases)
            .addAll(Compiler.networkElaborationPhases())
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(schedulePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new MlirPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
