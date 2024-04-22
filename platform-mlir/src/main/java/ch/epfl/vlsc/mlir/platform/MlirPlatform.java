package ch.epfl.vlsc.mlir.platform;

import ch.epfl.vlsc.mlir.phase.MlirPhase;
import ch.epfl.vlsc.phases.CommonPhases;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class MlirPlatform implements Platform {
    @Override
    public String name() {
        return "mlir";
    }

    @Override
    public String description() {
        return "A platform for generating MLIR for SystemVerilog generation through the CIRCT toolchain.";
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
        //return ImmutableList.<Phase>builder().build();
        return phases;
    }
}
