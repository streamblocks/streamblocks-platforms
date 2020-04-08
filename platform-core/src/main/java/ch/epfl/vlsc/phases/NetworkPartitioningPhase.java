package ch.epfl.vlsc.phases;


import ch.epfl.vlsc.compiler.PartitionedCompilationTask;
import ch.epfl.vlsc.settings.PlatformSettings;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.ToolAttribute;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.settings.Setting;


import java.lang.reflect.Type;
import java.util.*;

import java.util.stream.Collectors;

import ch.epfl.vlsc.compiler.PartitionedCompilationTask.PartitionKind;

public class NetworkPartitioningPhase implements Phase {


    private final String partitionKey = "partition";

    private Map<String, PartitionKind> partition;

    @Override
    public List<Setting<?>> getPhaseSettings() {

        return ImmutableList.of(PlatformSettings.PartitionNetwork);
    }
    @Override
    public String getDescription() {
        return "partitions the network based on the given attributes";
    }

    @Override
    public PartitionedCompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        partition = new HashMap<String, PartitionKind>();
        partition.put("sw", PartitionKind.SW);
        partition.put("hw", PartitionKind.HW);
        if(PlatformSettings.PartitionNetwork.read("on").isPresent()) {
            Map<PartitionKind, Network> networks = partitionNetwork(task, context);
            return PartitionedCompilationTask.of(task, networks);

        } else {
            return PartitionedCompilationTask.of(task);
        }

    }

    private PartitionKind getInstancePartition(Instance instance, Context context) {

        PartitionKind defaultPartition = PartitionKind.SW;

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
        Map<PartitionKind, List<Instance>> partToInst = new HashMap();
        Map<Instance, PartitionKind> instToPart = new HashMap();
        Map<PartitionKind, List<Connection>> partToCon = new HashMap();
        Map<PartitionKind, List<PortDecl>> partToInputs = new HashMap();
        Map<PartitionKind, List<PortDecl>> partToOutputs = new HashMap();

        List<Connection> connections =
                network.getConnections()
                        .stream()
                        .filter(c -> c.getSource().getInstance().isPresent())
                        .filter(c -> c.getTarget().getInstance().isPresent())
                        .collect(Collectors.toList());
        if (connections.size() != network.getConnections().size())
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "CAL network should not floating I/O"));

        for (Instance inst : network.getInstances()) {
            PartitionKind p = getInstancePartition(inst, context);
            instToPart.put(inst, p);
            partToInst.putIfAbsent(p, new ArrayList<>());
            partToInst.get(p).add(inst);
        }


        List<Connection> fanouts =
                connections.stream()
                        .filter(c1 ->
                            connections.stream().anyMatch(c2 ->
                                c2.getSource().getInstance().get().equals(c1.getSource().getInstance().get()) &&
                                c2.getSource().getPort().equals(c1.getSource().getPort()) && !c2.equals(c1)
                                )
                            ).collect(Collectors.toList());
        Map<Connection, Connection.End> fanoutSource = new HashMap<>();
        Map<Connection.End, Boolean> fanoutHasSourcePort = new HashMap<>();
        Map<Connection.End, PortDecl> fanoutPort = new HashMap<>();

        fanouts.stream().forEach(c -> fanoutSource.put(c, c.getSource()));
        fanoutSource.values().forEach(s -> fanoutHasSourcePort.put(s, false));
        // get internal connections of a sub network
        for (Connection con: connections) {
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

                partToCon.putIfAbsent(sourcePartition, new ArrayList<>());
                partToCon.get(sourcePartition).add(con);

            } else {

                Entity srcEntity=
                        task.getModule(GlobalNames.key)
                                .entityDecl(source.getEntityName(), true).getEntity();
                Entity tgtEntity =
                        task.getModule(GlobalNames.key)
                                .entityDecl(target.getEntityName(), true).getEntity();

                partToOutputs.putIfAbsent(sourcePartition, new ArrayList<>());
                partToInputs.putIfAbsent(targetPartition, new ArrayList<>());
                partToCon.putIfAbsent(sourcePartition, new ArrayList<>());
                partToCon.putIfAbsent(targetPartition, new ArrayList<>());

                PortDecl entityOutput =
                        srcEntity.getOutputPorts()
                                .stream().filter(p -> p.getName().equals(con.getSource().getPort()))
                                .findFirst().orElseThrow(
                                () -> new CompilationException(
                                        new Diagnostic(Diagnostic.Kind.ERROR,
                                                "Connection " + con.getSource().getPort() +
                                                        " of instance " + con.getSource().getInstance().get() +
                                                        " is not valid")));

                PortDecl entityInput =
                        tgtEntity.getInputPorts()
                                .stream().filter(p -> p.getName().equals(con.getTarget().getPort()))
                                .findFirst().orElseThrow(
                                () -> new CompilationException(
                                        new Diagnostic(Diagnostic.Kind.ERROR,
                                                "Connection " + con.getSource().getPort() +
                                                        " of instance " + con.getTarget().getInstance().get() +
                                                        " is not valid")));



                if (fanoutSource.containsKey(con)) {
                    // this is a cross-partition fanout connection
                    if (!fanoutHasSourcePort.get(fanoutSource.get(con))) {
                        PortDecl inputFO = new PortDecl(
                                con.getSource().getInstance().get() + "_" + con.getSource().getPort(),
                                (TypeExpr) entityOutput.getType().deepClone());

                        PortDecl outputFO= new PortDecl(
                                con.getSource().getInstance().get() + "_" + con.getSource().getPort(),
                                (TypeExpr) entityOutput.getType().deepClone());

                        fanoutPort.put(fanoutSource.get(con), inputFO);

                        partToOutputs.get(sourcePartition).add(outputFO);
                        partToInputs.get(targetPartition).add(inputFO);

                        fanoutHasSourcePort.put(fanoutSource.get(con), true);
                        Connection srcCon =
                                new Connection(
                                        new Connection.End(con.getSource().getInstance(), con.getSource().getPort()),
                                        new Connection.End(Optional.empty(),
                                                con.getSource().getInstance().get() + "_" + con.getSource().getPort()));
                        partToCon.get(sourcePartition).add(srcCon);
                    }

                    Connection tgtCon =
                            new Connection(
                                    new Connection.End(Optional.empty(), fanoutPort.get(fanoutSource.get(con)).getName()),
                                    new Connection.End(con.getTarget().getInstance(), con.getTarget().getPort()));
                    partToCon.get(targetPartition).add(tgtCon);
                } else {
                    PortDecl input = new PortDecl(
                            con.getTarget().getInstance().get() + "_" + con.getTarget().getPort(),
                            (TypeExpr) entityInput.getType().deepClone());
                    PortDecl output = new PortDecl(
                            con.getSource().getInstance().get() + "_" + con.getSource().getPort(),
                            (TypeExpr) entityOutput.getType().deepClone());
                    // this is a normal cross-partition conneciton, should become a 2 ports and 2 connection
                    partToOutputs.get(sourcePartition).add(output);
                    partToInputs.get(targetPartition).add(input);
                    Connection srcCon =
                            new Connection(
                                    new Connection.End(con.getSource().getInstance(), con.getSource().getPort()),
                                    new Connection.End(Optional.empty(),
                                            con.getSource().getInstance().get() + "_" + con.getSource().getPort()));
                    Connection tgtCon =
                            new Connection(
                                    new Connection.End(Optional.empty(),
                                            con.getTarget().getInstance().get() + "_" + con.getTarget().getPort()),
                                    new Connection.End(con.getTarget().getInstance(), con.getTarget().getPort()));

                    partToCon.get(sourcePartition).add(srcCon);
                    partToCon.get(targetPartition).add(tgtCon);
                }


            }

        }


        Map<PartitionKind, Network> nets = new HashMap();
        for (PartitionKind p: partToInst.keySet()) {
            nets.put(p, new Network(
                    partToInputs.get(p),
                    partToOutputs.get(p),
                    partToInst.get(p),
                    partToCon.get(p)));
        }

        return nets;
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
