package ch.epfl.vlsc.unity.platform;

// import ch.epfl.vlsc.hls.phase.VivadoHLSBackendPhase;
import ch.epfl.vlsc.phases.AddFanoutPhase;
import ch.epfl.vlsc.phases.CalToAmHwPhase;
import ch.epfl.vlsc.phases.LiftExprInputFromScopesPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class UnityPlatform implements Platform {
    @Override
    public String name() {
        return "unity";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for unified sw/hw synthesis.";
    }

    public static List<Phase> networkElaborationPhases() {
        return ImmutableList.of(
                new CreateNetworkPhase()
        );
    }

    

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(networkElaborationPhases())
            // .addAll(actorMachinePhases())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}