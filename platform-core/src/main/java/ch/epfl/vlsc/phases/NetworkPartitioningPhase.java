package ch.epfl.vlsc.phases;


import ch.epfl.vlsc.compiler.PartitionedCompilationTask;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.TypeScopes;
import se.lth.cs.tycho.attribute.VariableDeclarations;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.*;

import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;

import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.type.NominalTypeExpr;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.op.Binary;
import se.lth.cs.tycho.meta.interp.op.Unary;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.settings.Setting;
import se.lth.cs.tycho.meta.interp.value.util.Convert;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.epfl.vlsc.compiler.PartitionedCompilationTask.PartitionKind;

/**
 * @author Mahyar Emami (mahyar.emami@epfl.ch)
 * Partition the network into HW and SW partitions, the phase returns the same task but with an augmented field
 * that is a map from partition kind to the a network partition.
 * Later phases should take the PartitionedCompilationTask and extract the desired network partitions from it.
 */
public class NetworkPartitioningPhase implements Phase {



    public static final String partitionKey = PartitionedCompilationTask.partitionKey;

    private Map<String, PartitionKind> partition;

    @Override
    public List<Setting<?>> getPhaseSettings() {

        return ImmutableList.of(PlatformSettings.PartitionNetwork, PlatformSettings.defaultPartition);
    }

    @Override
    public String getDescription() {
        return "partitions the network based on the given attributes";
    }

    @Override
    public Set<Class<? extends Phase>> dependencies() {
        return Collections.singleton(VerilogNameCheckerPhase.class);
    }

    @Override
    public PartitionedCompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        partition = new HashMap<String, PartitionKind>();
        partition.put("sw", PartitionKind.SW);
        partition.put("hw", PartitionKind.HW);

        context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO, "Partitioning the CAL network"));
        context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO, "Partitions kinds are " +
                String.join(", ",
                        Arrays.stream(PartitionKind.values())
                                .map(PartitionKind::toString)
                                .collect(Collectors.toList())) + "."));

        Boolean paritioningEnabled = context.getConfiguration().isDefined(PlatformSettings.PartitionNetwork)
                && context.getConfiguration().get(PlatformSettings.PartitionNetwork);
        if (paritioningEnabled) {
            Map<PartitionKind, Network> networks = partitionNetwork(task, context);
            return PartitionedCompilationTask.of(task, networks);

        } else {
            return PartitionedCompilationTask.of(task);
        }

    }

    /**
     * Finds the partition of an instance, fails if the provided attribute is wrong
     *
     * @param instance
     * @param context
     * @return
     */
    private PartitionKind getInstancePartition(Instance instance, Context context) {

        PartitionKind defaultPartition = PlatformSettings.defaultPartition.defaultValue(context.getConfiguration());
        if (context.getConfiguration().isDefined(PlatformSettings.defaultPartition))
            defaultPartition = context.getConfiguration().get(PlatformSettings.defaultPartition);

        ImmutableList<ToolAttribute> pattrs =
                ImmutableList.from(instance.getAttributes()
                        .stream().filter(a -> a.getName().equals(partitionKey)).collect(Collectors.toList()));


        if (pattrs.size() == 0) {
//            context
//                    .getReporter()
//                    .report(
//                            new Diagnostic(Diagnostic.Kind.WARNING,
//                                    "No partition attribute specified for instance "
//                                            + instance.getInstanceName() +
//                                            ", using default partition " + defaultPartition.toString() + ".\n" +
//                                    "Use attribute partition=hw|sw in actor instantiation to manually set partitions."));
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

    /**
     * Partitions the network based on the attributes given in the network file.
     *
     * @param task
     * @param context
     * @return
     * @throws CompilationException
     */
    private Map<PartitionKind, Network> partitionNetwork(CompilationTask task,
                                                         Context context) throws CompilationException {

        Network network = task.getNetwork();
        Map<PartitionKind, List<Instance>> partToInst = new HashMap<>();
        Map<Instance, PartitionKind> instToPart = new HashMap<>();
        Map<PartitionKind, List<Connection>> partToCon = new HashMap<>();
        Map<PartitionKind, List<PortDecl>> partToInputs = new HashMap<>();
        Map<PartitionKind, List<PortDecl>> partToOutputs = new HashMap<>();

        Interpreter interpreter = MultiJ.from(Interpreter.class)
                .bind("variables").to(task.getModule(VariableDeclarations.key))
                .bind("types").to(task.getModule(TypeScopes.key))
                .bind("unary").to(MultiJ.from(Unary.class).instance())
                .bind("binary").to(MultiJ.from(Binary.class).instance())
                .instance();

        Convert convert = MultiJ.from(Convert.class)
                .instance();

        EvalNominalTypeExpr evalNominalTypeExpr = MultiJ.from(EvalNominalTypeExpr.class)
                .bind("interpreter").to(interpreter)
                .bind("convert").to(convert)
                .instance();

        List<Connection> connections =
                network.getConnections()
                        .stream()
                        .filter(c -> c.getSource().getInstance().isPresent())
                        .filter(c -> c.getTarget().getInstance().isPresent())
                        .collect(Collectors.toList());
        if (connections.size() != network.getConnections().size())
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "CAL network should not have floating I/O"));

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
        for (Connection con : connections) {
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
                /**
                 * The source and target are on different partitions. In this case any connection
                 * that is of the form SOURCE_INST.OUT_PORT --> TARGET_INST.IN_PORT
                 * should be replaced by an output  port declaration in the source partition
                 * that is named as
                 */
                Entity srcEntity =
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


                String portName = getPortNameFromConnection(con);
                if (fanoutSource.containsKey(con)) {
                    // this is a cross-partition fanout connection
                    if (!fanoutHasSourcePort.get(fanoutSource.get(con))) {
                        PortDecl inputFO = new PortDecl(
                                portName,
                                evalNominalTypeExpr.eval(entityOutput.getType()));

                        PortDecl outputFO = new PortDecl(
                                portName,
                                evalNominalTypeExpr.eval(entityOutput.getType()));

                        fanoutPort.put(fanoutSource.get(con), inputFO);

                        partToOutputs.get(sourcePartition).add(outputFO);
                        partToInputs.get(targetPartition).add(inputFO);

                        fanoutHasSourcePort.put(fanoutSource.get(con), true);
                        Connection srcCon =
                                new Connection(
                                        new Connection.End(con.getSource().getInstance(), con.getSource().getPort()),
                                        new Connection.End(Optional.empty(), portName))
                                        .withAttributes(con.getAttributes().map(ToolAttribute::deepClone));
                        partToCon.get(sourcePartition).add(srcCon);
                    }

                    Connection tgtCon =
                            new Connection(
                                    new Connection.End(Optional.empty(), fanoutPort.get(fanoutSource.get(con)).getName()),
                                    new Connection.End(con.getTarget().getInstance(), con.getTarget().getPort()))
                                    .withAttributes(con.getAttributes().map(ToolAttribute::deepClone));
                    partToCon.get(targetPartition).add(tgtCon);
                } else {
                    PortDecl input = new PortDecl(portName, evalNominalTypeExpr.eval(entityInput.getType()));
                    PortDecl output = new PortDecl(portName, evalNominalTypeExpr.eval(entityOutput.getType()));
                    // this is a normal cross-partition conneciton, should become a 2 ports and 2 connection
                    partToOutputs.get(sourcePartition).add(output);
                    partToInputs.get(targetPartition).add(input);
                    Connection srcCon =
                            new Connection(
                                    new Connection.End(con.getSource().getInstance(), con.getSource().getPort()),
                                    new Connection.End(Optional.empty(), portName))
                                    .withAttributes(con.getAttributes().map(ToolAttribute::deepClone));
                    Connection tgtCon =
                            new Connection(
                                    new Connection.End(Optional.empty(), portName),
                                    new Connection.End(con.getTarget().getInstance(), con.getTarget().getPort()))
                                    .withAttributes(con.getAttributes().map(ToolAttribute::deepClone));

                    partToCon.get(sourcePartition).add(srcCon.withAttributes(con.getAttributes().map(ToolAttribute::deepClone)));
                    partToCon.get(targetPartition).add(tgtCon.withAttributes(con.getAttributes().map(ToolAttribute::deepClone)));
                }


            }

        }


        Map<PartitionKind, Network> nets = new HashMap();
        for (PartitionKind p : partToInst.keySet()) {
            nets.put(p, new Network(ImmutableList.from(network.getAnnotations()),
                    partToInputs.get(p),
                    partToOutputs.get(p),
                    partToInst.get(p),
                    partToCon.get(p)));
        }

        return nets;
    }

    private String getPortNameFromConnection(Connection connection) {
        return connection.getSource().getInstance().get() + "_" +
                connection.getSource().getPort() + "_" + connection.getTarget().getInstance().get() + "_" +
                connection.getTarget().getPort();
    }

    private Optional<Instance> findInstanceByName(Network network, String name) {
        Optional<Instance> instance = Optional.empty();
        for (Instance inst : network.getInstances()) {
            if (inst.getInstanceName().equals(name))
                instance = Optional.of(inst);
        }
        return instance;
    }

    @Module
    interface EvalNominalTypeExpr {

        @Binding(BindingKind.INJECTED)
        Interpreter interpreter();

        @Binding(BindingKind.INJECTED)
        Convert convert();

        default TypeExpr eval(TypeExpr typeExpr) {
            return typeExpr;
        }

        default TypeExpr eval(NominalTypeExpr typeExpr) {
            ImmutableList.Builder<TypeParameter> typeParameters = ImmutableList.builder();
            ImmutableList.Builder<ValueParameter> valueParameters = ImmutableList.builder();

            typeParameters.addAll(typeExpr.getTypeParameters());

            for (ValueParameter valueParameter : typeExpr.getValueParameters()) {
                Expression expression = valueParameter.getValue();
                Environment env = new Environment();
                Value value = interpreter().eval(expression, env);
                ValueParameter newValueParameter = new ValueParameter(valueParameter.getName(), convert().apply(value));
                valueParameters.add(newValueParameter);
            }

            return new NominalTypeExpr(typeExpr.getName(), typeParameters.build(), valueParameters.build());
        }

    }

}
