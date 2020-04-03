package com.streamgenomics.platform;

import ch.epfl.vlsc.hls.platform.VivadoHLS;
import com.streamgenomics.phase.OpalKellyBackendPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class OpalKelly implements Platform {
    @Override
    public String name() {
        return "opal-kelly";
    }

    @Override
    public String description() {
        return "StreamBlocks platform for OpalKelly FPGAs boards";
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(VivadoHLS.networkElaborationPhases())
            .addAll(VivadoHLS.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new OpalKellyBackendPhase())
            .build();


    @Override
    public List<Phase> phases() {
        return phases;
    }
}