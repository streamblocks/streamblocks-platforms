package ch.epfl.vlsc.cpp.platform;

import ch.epfl.vlsc.phases.AddFanoutPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.platform.Platform;
import ch.epfl.vlsc.cpp.phase.CppBackendPhase;

import java.util.List;

public class Cpp implements Platform {
    @Override
    public String name() {
        return "cpp";
    }

    @Override
    public String description() {
        return "StreamBlocks Cpp Platform";
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.templatePhases())
            .addAll(Compiler.networkElaborationPhases())
            .add(new AddFanoutPhase())
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new CppBackendPhase())
            .build();


    @Override
    public List<Phase> phases() {
        return phases;
    }
}
