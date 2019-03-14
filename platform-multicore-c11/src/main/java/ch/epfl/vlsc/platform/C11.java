package ch.epfl.vlsc.platform;

import ch.epfl.vlsc.phase.C11BackendPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class C11 implements Platform {
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
            .addAll(Compiler.networkElaborationPhases())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new C11BackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
