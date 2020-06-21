package ch.epfl.vlsc.analysis.partitioning.paltforms;

import ch.epfl.vlsc.analysis.partitioning.phase.PartitioningAnalysisPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class PartitioningAnalysisPlatform implements Platform {

    @Override
    public String name() {
        return "partition";
    }

    @Override
    public String description() {
        return "StreamBlocks partition analysis using ILP.";
    }


    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.networkElaborationPhases())
            .add(new PartitioningAnalysisPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }

}
