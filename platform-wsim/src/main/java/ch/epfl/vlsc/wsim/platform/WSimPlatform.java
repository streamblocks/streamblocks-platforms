package ch.epfl.vlsc.wsim.platform;

import ch.epfl.vlsc.phases.CommonPhases;
import ch.epfl.vlsc.wsim.phase.*;
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
//            .addAll(new SubstituteTaskNullValueParameters())
            .addAll(Compiler.templatePhases())
            .addAll(CommonPhases.networkElaborationPhases)
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
//            .add(new CppTypeConversionPhase()) // convert all type expression to cpp nominal types
//            .add(new CppRenameVariablesPhase()) // rename all variable declaration
//            .add(new ActorMachineToCppClassConversionPhase()) // transform ActorMachines to c++ class declarations
//            .add(new CppNominalTypeAssertionPhase()) // check the tree for nominal cpp types
//            .add(new PrependStreamBlocksToNameSpacesPhase())
            .add(new WarpActorCodeGenerationPhase())
            .build();

    @Override
    public  List<Phase> phases() {
        return phases;
    }
}
