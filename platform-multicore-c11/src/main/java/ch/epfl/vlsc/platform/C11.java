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
        return "multicore-c11";
    }

    @Override
    public String description() {
        return "A backend for multicore C11 code.";
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
