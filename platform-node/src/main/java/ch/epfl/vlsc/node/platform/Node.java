package ch.epfl.vlsc.node.platform;

import ch.epfl.vlsc.node.phase.NodePhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class Node implements Platform {

    @Override
    public String name() {
        return "node";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for Node runtime.";
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.networkElaborationPhases())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new NodePhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
