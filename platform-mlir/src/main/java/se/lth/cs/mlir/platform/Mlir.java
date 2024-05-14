package se.lth.cs.mlir.platform;

import se.lth.cs.mlir.phase.MlirPhase;
import ch.epfl.vlsc.phases.*;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class Mlir implements Platform {

    @Override
    public String name() {
        return "mlir";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for Mlir runtime.";
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.templatePhases())
            .addAll(CommonPhases.portEnumerationPhases)
            .addAll(Compiler.networkElaborationPhases())
            .addAll(Compiler.nameAndTypeAnalysis())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new MlirPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
