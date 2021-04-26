package ch.epfl.vlsc.hls.platform;

import ch.epfl.vlsc.hls.phase.BankedNetworkPortsPhase;
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

    public static ImmutableList<String> externalsToIgnore = ImmutableList.of(
            "displayYUV_init",
            "displayYUV_displayPicture",
            "displayYUV_getFlags",
            "displayYUV_getNbFrames",
            "compareYUV_init",
            "compareYUV_comparePicture",
            "fpsPrintInit",
            "fpsPrintNewPicDecoded",
            "source_init",
            "source_readNBytes",
            "source_sizeOfFile",
            "source_rewind",
            "source_decrementNbLoops",
            "source_isMaxLoopsReached",
            "source_exit",
            "sin",
            "rand",
            "timeMSec",
            "random",
            "randInt"
    );

    public static List<Phase> postPartitionNetworkElaborationPhases() {
        return ImmutableList.of(
                new BankedNetworkPortsPhase(),
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
                new ConstantVariableInitializationPhase(),
                new NameAnalysisPhase(),
                new OldExprVariableSupportPhase()
        );
    }


    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(frontendPhases())
            .addAll(Compiler.templatePhases())
            .add(new RemovePrintPhase())
            .add(new RecursiveTypeDetectionPhase())
            .addAll(CommonPhases.networkElaborationPhases)
            .add(new VerilogNameCheckerPhase())
            .addAll(Compiler.nameAndTypeAnalysis())
//            .add(new XcfAnnotationPhase())
            .addAll(CommonPhases.hardwarePartitioningPhases)
            .addAll(postPartitionNetworkElaborationPhases())
            .addAll(actorMachinePhases())
            .add(new ExprOutputToAssignment())
            .add(new ExprToStmtAssignment())
            .add(new ListComprehensionToStmtWhile())
            .add(new SequentialPortAccess())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new VivadoHLSBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
