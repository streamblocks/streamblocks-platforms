package ch.epfl.vlsc.sw.platform;

import ch.epfl.vlsc.phases.*;
import ch.epfl.vlsc.sw.phase.CreatePartitionLinkPhase;
import ch.epfl.vlsc.sw.phase.MultiCoreBackendPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class Multicore implements Platform {
    @Override
    public String name() {
        return "multicore";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for multicore platforms that uses PThread.";
    }


    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.templatePhases())
            .addAll(CommonPhases.networkElaborationPhases)
            .add(new VerilogNameCheckerPhase())
            .addAll(Compiler.nameAndTypeAnalysis())
            .add(new XcfAnnotationPhase())
            .addAll(CommonPhases.softwarePartitioningPhases)
            .addAll(Compiler.actorMachinePhases())
//            .add(new RemoveUnusedEntityDeclsPhase()) // This can not happen after network elaboration because the hardware partition entities get removed.
            .add(new CreatePartitionLinkPhase())
            .add(new MultiCoreBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
