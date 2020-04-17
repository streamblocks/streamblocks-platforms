package ch.epfl.vlsc.orcc.platform;

import ch.epfl.vlsc.orcc.phase.OrccBackendPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class Orcc implements Platform {
    @Override
    public String name() {
        return "Orcc";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for RVC decoders using Orcc C runtime.";
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.networkElaborationPhases())
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new OrccBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
