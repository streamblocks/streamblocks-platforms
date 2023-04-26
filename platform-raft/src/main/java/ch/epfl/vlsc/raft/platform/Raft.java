package ch.epfl.vlsc.raft.platform;

import ch.epfl.vlsc.phases.AddFanoutPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import ch.epfl.vlsc.phases.*;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.platform.Platform;
import ch.epfl.vlsc.raft.phase.RaftBackendPhase;

import java.util.List;

public class Raft implements Platform {
    @Override
    public String name() {
        return "raft";
    }

    @Override
    public String description() {
        return "StreamBlocks Raft Platform";
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.templatePhases())
            .addAll(CommonPhases.portEnumerationPhases)
            .addAll(Compiler.networkElaborationPhases())
            .add(new AddFanoutPhase())
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new RaftBackendPhase())
            .build();


    @Override
    public List<Phase> phases() {
        return phases;
    }
}
