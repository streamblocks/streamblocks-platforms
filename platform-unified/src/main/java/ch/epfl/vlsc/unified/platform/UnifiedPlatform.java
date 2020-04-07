package ch.epfl.vlsc.unified.platform;


import ch.epfl.vlsc.phases.AddFanoutPhase;
import ch.epfl.vlsc.phases.CalToAmHwPhase;
import ch.epfl.vlsc.phases.LiftExprInputFromScopesPhase;
import ch.epfl.vlsc.unified.phase.DeclarePartitionLinkPhase;
import ch.epfl.vlsc.unified.phase.NetworkPartitionPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.*;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class UnifiedPlatform implements Platform {
    @Override
    public String name() {
        return "unified";
    }

    @Override
    public String description() {
        return "StreamBlocks code-generator for unified sw/hw synthesis.";
    }

    public static List<Phase> networkElaborationPhases() {
        return ImmutableList.of(
            new CreateNetworkPhase(),
            new ResolveGlobalEntityNamesPhase(),
            new ResolveGlobalVariableNamesPhase(),
            new ElaborateNetworkPhase(),
            new RemoveUnusedGlobalDeclarations()
        );
    }

    

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(networkElaborationPhases())
            .add(new DeclarePartitionLinkPhase())
            .add(new NetworkPartitionPhase())
            // .addAll(actorMachinePhases())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
