package ch.epfl.vlsc.orcc.platform;

import ch.epfl.vlsc.orcc.phase.OrccBackendPhase;
import ch.epfl.vlsc.phases.ExprOutputToAssignment;
import ch.epfl.vlsc.phases.ExprToStmt;
import ch.epfl.vlsc.phases.ListComprehensionToStmtWhile;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class Orcc implements Platform {
    @Override
    public String name() {
        return "orcc";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for RVC decoders using Orcc C runtime.";
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(frontendPhases())
            .addAll(Compiler.templatePhases())
            .addAll(Compiler.networkElaborationPhases())
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(Compiler.actorMachinePhases())
            .add(new ExprOutputToAssignment())
            .add(new ExprToStmt())
            .add(new ListComprehensionToStmtWhile())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new OrccBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }

    public static ImmutableList<Phase> frontendPhases() {
        return ImmutableList.of(
                // Parse
                new LoadEntityPhase(),
                new LoadPreludePhase(),
                new LoadImportsPhase(),

                // For debugging
                new PrintLoadedSourceUnits(),
                new PrintTreesPhase(),

                // Post parse
                new RemoveExternStubPhase(),
                new OperatorParsingPhase(),
                new ImportAnalysisPhase(),
                new ResolvePatternDeconstructionPhase(),
                new DeclarationAnalysisPhase(),
                new ResolveTypeConstructionPhase(),
               // new ConstantVariableImmutabilityPhase(),
                new ConstantVariableInitializationPhase(),
                new NameAnalysisPhase()
        );
    }
}
