package ch.epfl.vlsc.phases;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;

public class CommonPhases {


    public static final ImmutableList<Phase> networkElaborationPhases = ImmutableList.of(
            new CreateNetworkPhase(),
            new ResolveGlobalEntityNamesPhase(),
            new ResolveGlobalVariableNamesPhase(),
            //new ConnectivityAnalysisPhase(),
            new ElaborateNetworkPhase(),
            new RemoveUnusedGlobalDeclarations()
            , new AddFanoutPhase()
    );


    public static final ImmutableList<Phase> partitioningPhases = ImmutableList.of(
            new XcfAnnotationPhase(),
            new VerilogNameCheckerPhase(),
            new NetworkPartitioningPhase(),
            new AnnotateExternalMemories()
    );

    public static final ImmutableList<Phase> hardwarePartitioningPhases = ImmutableList.<Phase>builder()
            .addAll(CommonPhases.partitioningPhases)
            .add(new ExtractHardwarePartition())
            .build();


    public static final ImmutableList<Phase> softwarePartitioningPhases = ImmutableList.<Phase>builder()
            .addAll(CommonPhases.partitioningPhases)
            .add(new ExtractSoftwarePartition())
            .build();


    public static final ImmutableList<Phase> portEnumerationPhases = ImmutableList.of(
            new ActionGeneratorEnumeration(),
            new PortArrayCollectionExpansion(),
            new PortArrayEnumeration(),
            new PostPortArrayEnumerationNameAnalysis()
    );
}
