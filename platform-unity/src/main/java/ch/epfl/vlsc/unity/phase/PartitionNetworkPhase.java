package ch.epfl.vlsc.unity.phase;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.settings.Setting;

import java.util.List;
import java.util.Set;

public class PartitionNetworkPhase implements Phase {

    @Override
    public String getName() {
        return "PartitionNetwork";
    }

    @Override
    public String getDescription() {
        return "Partitions the network based on instance attribute \"partition\"";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        return task;
    }

    private ImmutableList<Network> partitionNetwork(Network network) {


        return ImmutableList.of(network);
    }


}
