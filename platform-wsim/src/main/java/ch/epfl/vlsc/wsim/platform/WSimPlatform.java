package ch.epfl.vlsc.wsim.platform;

import ch.epfl.vlsc.phases.CommonPhases;
import ch.epfl.vlsc.wsim.phase.CppTypeConversionPhase;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.platform.Platform;
import se.lth.cs.tycho.compiler.Compiler;
import java.util.List;

public class WSimPlatform implements Platform {

    @Override
    public String name() { return "WarpSimulation"; }

    @Override
    public String description() { return "Warp Simulation platform"; }

    private static ImmutableList<Phase> frontendPhases() {
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
                new NameAnalysisPhase(),
                new OldExprVariableSupportPhase()
        );
    }
    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(frontendPhases())
            .addAll(Compiler.templatePhases())
            .addAll(CommonPhases.networkElaborationPhases)
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new CppTypeConversionPhase())
            .build();

    @Override
    public  List<Phase> phases() {
        return phases;
    }
}
