package ch.epfl.vlsc.unified.phase;

import ch.epfl.vlsc.unified.ir.network.PartitionLink;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.ToolAttribute;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import ch.epfl.vlsc.unified.ir.PartitionKind;

import java.util.AbstractMap;
import java.util.List;

import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Optional;

public class NetworkPartitionPhase implements Phase {

    private final String partitionKey = "partition";

    private final Map<String, PartitionKind> partition = Map.ofEntries(
            new AbstractMap.SimpleEntry<String, PartitionKind>("sw", PartitionKind.SW),
            new AbstractMap.SimpleEntry<String, PartitionKind>("hw", PartitionKind.HW)
    );

    @Override
    public String getDescription() {
        return "partitions the network based on the given attributes";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        partitionNetwork(task, context);
        return task;
    }

    private PartitionKind getInstancePartition(Instance instance, Context context) {

        PartitionKind defaultPartition = PartitionKind.HW;

        ImmutableList<ToolAttribute> pattrs =
                ImmutableList.from(instance.getAttributes()
                        .stream().filter(a -> a.getName().equals(partitionKey)).collect(Collectors.toList()));
        if (pattrs.size() == 0) {
            context
                    .getReporter()
                    .report(
                            new Diagnostic(Diagnostic.Kind.INFO,
                                    "No partition attribute specified for instance " + instance.getInstanceName()));
            return defaultPartition;
        } else if (pattrs.size() == 1) {
            if (pattrs.get(0) instanceof ToolValueAttribute) {
                ToolValueAttribute attr = (ToolValueAttribute) pattrs.get(0);
                String p = ((ExprLiteral) attr.getValue()).asString().get();
                return partition.get(p);
            } else {
                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "partition attribute of instance " +
                                        instance.getInstanceName() + "should be a value not a type")
                );
                return defaultPartition;
            }
        } else {
            context
                    .getReporter()
                    .report(
                            new Diagnostic(Diagnostic.Kind.INFO,
                                    "using default sw partition for " + instance.getInstanceName()));
            return defaultPartition;
        }

    }

    private Map<PartitionKind, Network> partitionNetwork(CompilationTask task,
                                                         Context context) throws CompilationException {

        Network network = task.getNetwork();
        Map<PartitionKind, ImmutableList.Builder<Instance>> partToInst = new HashMap();
        Map<Instance, PartitionKind> instToPart = new HashMap();
        ImmutableList<Connection> connections = network.getConnections();

        for (Instance inst : network.getInstances()) {
            PartitionKind p = getInstancePartition(inst, context);
            instToPart.put(inst, p);
            if (partToInst.containsKey(p))
                partToInst.get(p).add(inst);
            else
                partToInst.put(p, ImmutableList.builder());
        }

        Map<PartitionKind, ImmutableList.Builder<Connection>> partToCon = new HashMap();

        // get internal connections of a sub network
        for (Connection con: connections) {
            if (con.getSource().getInstance().isPresent() && con.getTarget().getInstance().isPresent()) {
                Instance source = findInstanceByName(network, con.getSource().getInstance().get()).orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(Diagnostic.Kind.ERROR,
                                                "Could not find source instance for connection " +
                                                        con.getSource().getInstance().get() + "." +
                                                        con.getSource().getPort() + " --> " +
                                                        con.getTarget().getInstance().get() + "." +
                                                        con.getTarget().getInstance().get())
                            )
                        );
                Instance target = findInstanceByName(network, con.getTarget().getInstance().get()).orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(Diagnostic.Kind.ERROR,
                                        "Could not find target instance for connection " +
                                                con.getSource().getInstance().get() + "." +
                                                con.getSource().getPort() + " --> " +
                                                con.getTarget().getInstance().get() + "." +
                                                con.getTarget().getInstance().get())
                        )
                );

                PartitionKind sourcePartition = instToPart.get(source);
                PartitionKind targetPartition = instToPart.get(target);
                // Connection in the same partition are treated as before
                if (sourcePartition == targetPartition) {

                    if (partToCon.containsKey(sourcePartition))
                        partToCon.get(sourcePartition).add(con);
                    else
                        partToCon.put(sourcePartition, ImmutableList.builder());
                } else if (sourcePartition != targetPartition) {

                }
            } else {
                // error
            }
        }
        Map<PartitionKind, Network> nets = new HashMap();


        return null;
    }

    private Optional<Instance> findInstanceByName(Network network, String name) {
        Optional<Instance> instance = Optional.empty();
        for (Instance inst: network.getInstances()) {
            if (inst.getInstanceName().equals(name))
                instance = Optional.of(inst);
        }
        return instance;
    }

}
