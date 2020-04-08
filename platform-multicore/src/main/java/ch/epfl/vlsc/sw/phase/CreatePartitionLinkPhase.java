package ch.epfl.vlsc.sw.phase;

import ch.epfl.vlsc.phases.ExtractSoftwarePartition;
import ch.epfl.vlsc.phases.NetworkPartitioningPhase;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.ir.PartitionLink;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.compiler.SyntheticSourceUnit;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.decl.Availability;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CreatePartitionLinkPhase implements Phase {


    @Override
    public String getDescription() {
        return "Creates a PartitionLink Entity that directs all floating connections to an instance of this entity";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        if (context.getConfiguration().isDefined(PlatformSettings.PartitionNetwork)
                && context.getConfiguration().get(PlatformSettings.PartitionNetwork))
            return createPartitionLink(task, context);
        else {
            context.getReporter()
                    .report(
                            new Diagnostic(Diagnostic.Kind.INFO, "Skipping CreatePartitionLinkPhase " +
                                    "since the " + PlatformSettings.PartitionNetwork.getKey() +
                                    " setting was not found or was set to off"));
            return task;
        }

    }


    @Override
    public Set<Class<? extends Phase>> dependencies() {
        Set<Class<? extends Phase>> deps = new HashSet<>();
        deps.add(NetworkPartitioningPhase.class);
        deps.add(ExtractSoftwarePartition.class);
        return deps;
    }

    /**
     * Creates a PartitionLink entity and instance and directs all floating connections to the instance
     * @param task
     * @param context
     * @return a task with a new network that contains the PartitionLink instance and its corresponding connections
     * @throws CompilationException
     */
    private CompilationTask createPartitionLink(CompilationTask task, Context context) throws CompilationException {

        // -- Collect all the floating connections
        List<Connection> floatingSourceConnections =
                task.getNetwork().getConnections().stream()
                        .filter(c -> !c.getSource().getInstance().isPresent()).collect(Collectors.toList());
        List<Connection> floatingTargetConnections =
                task.getNetwork().getConnections().stream()
                        .filter(c -> !c.getTarget().getInstance().isPresent()).collect(Collectors.toList());
        if (floatingSourceConnections.size() == 0 && floatingTargetConnections.size() == 0) {
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Skipping PartitionLink creation since" +
                            " there are no floating connections.")
            );
            return task;
        }
        context.getReporter().report(
                new Diagnostic(Diagnostic.Kind.INFO, "Adding a PartitionLink entity to " +
                        task.getIdentifier().getLast()));
        // -- get the output ports from connections with missing source
        List<PortDecl> outputPorts =
                floatingSourceConnections.stream()
                        .map(c -> getPortDeclFromConnection(task, c, true)).collect(Collectors.toList());
        // -- get the input ports from connections with missing target
        List<PortDecl> inputPorts =
                floatingTargetConnections.stream()
                        .map(c -> getPortDeclFromConnection(task, c, false)).collect(Collectors.toList());

        String plinkEntityName = "system_plink";

        // -- create a PartitionLink entity
        PartitionLink plink = new PartitionLink(inputPorts, outputPorts);
        GlobalEntityDecl plinkEntity = GlobalEntityDecl.global(Availability.PUBLIC, plinkEntityName, plink, false);
        String plinkInstanceName = uniquePlinkName(task.getNetwork(), plinkEntityName);

        // -- create a PartitionLink instance
        Instance plinkInstance =
                new Instance(plinkInstanceName, QID.of(plinkInstanceName), null, null);

        // -- Connect the floating connections to the PartitionLink instance
        List<Connection> attachedConnections =
                Stream.concat(
                        floatingSourceConnections.stream()
                                .map(c ->
                                        c.withSource(new Connection.End(Optional.of(plinkInstanceName),
                                                c.getSource().getPort()))),
                        floatingTargetConnections.stream()
                                .map(c ->
                                        c.withTarget(new Connection.End(Optional.of(plinkInstanceName),
                                                c.getTarget().getPort())))
                ).collect(Collectors.toList());
        List<Connection> oldConnections =
                task.getNetwork().getConnections().stream()
                    .filter(c -> c.getSource().getInstance().isPresent() && c.getTarget().getInstance().isPresent())
                .collect(Collectors.toList());

        List<Connection> newConnections =
                Stream.concat(oldConnections.stream(), attachedConnections.stream()).collect(Collectors.toList());
        List<Instance> newInstances = new ArrayList<>();
        newInstances.add(plinkInstance);
        newInstances.addAll(task.getNetwork().getInstances());

        // -- create a new network with the added connections and instances
        Network newNetwork = task.getNetwork().withConnections(newConnections).withInstances(newInstances);
        SourceUnit plinkSource =
                new SyntheticSourceUnit(
                        new NamespaceDecl(QID.empty(),
                                null, null, ImmutableList.of(plinkEntity), null));
        List<SourceUnit> sourceUnits = new ArrayList<>(task.getSourceUnits());
        sourceUnits.add(plinkSource);
        // -- the new network should no longer have any floating connections
        boolean allConnected =
                newNetwork.getConnections()
                        .stream()
                        .allMatch(c ->
                                c.getSource().getInstance().isPresent() && c.getTarget().getInstance().isPresent());
        boolean plinkAdded =
                (newNetwork.getInstances().size() - task.getNetwork().getInstances().size()) == 1;
        if (!allConnected)
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Not all floating connections removed!"));
        if (!plinkAdded)
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "PartitionLink not added!"));
        CompilationTask newTask = task.withNetwork(newNetwork).withSourceUnits(sourceUnits);

        return newTask;
    }

    /**
     * Derives a PortDecl from a connection, it is used to create I/O ports from a connection
     * @param task - The CompilationTask in the phase
     * @param connection - the Connection for which a PortDecl is derived
     * @param fromTarget - if true, derives the PortDecl from the target of a connection (i.e. its type) otherwise
     *                      this function derives the port from the source end
     * @return - a port decl with its type derived from the designated end and its name derived from the other end of
     *           the connection
     */
    private PortDecl getPortDeclFromConnection(CompilationTask task, Connection connection, boolean fromTarget) {
        Optional<String> instanceNameOptional =
                fromTarget ? connection.getTarget().getInstance() : connection.getSource().getInstance();
        String instanceName =
                instanceNameOptional.orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(Diagnostic.Kind.ERROR, "Detected a connection with " +
                                                "an end point floating while trying to derive a PortDecl from " +
                                        "that end.")));
        Instance instance = findInstanceByName(task.getNetwork(), instanceName).orElseThrow(
                () -> new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, "Could not find instance" +
                                instanceName)));
        Entity entity =
                task.getModule(GlobalNames.key).entityDecl(instance.getEntityName(), true).getEntity();
        ImmutableList<PortDecl> entityPorts =
                fromTarget ? entity.getInputPorts() : entity.getOutputPorts();
        String portName = fromTarget ? connection.getTarget().getPort() : connection.getSource().getPort();
        PortDecl entityPort = entityPorts.stream().filter(p -> p.getName().equals(portName)).findFirst().orElseThrow(
                () -> new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, "Could not find port " + portName +
                                " in instance " + instanceName)));
        String declName = fromTarget ? connection.getSource().getPort() : connection.getTarget().getPort();
        TypeExpr declType = (TypeExpr) entityPort.getType().deepClone();
        return new PortDecl(declName, declType);
    }

    /**
     * Finds and instance in the network using its name
     * @param network the network of instances
     * @param name the name of the instance
     * @return an optional instance if a match was found
     */
    private Optional<Instance> findInstanceByName(Network network, String name) {
        Optional<Instance> instance = Optional.empty();
        for (Instance inst: network.getInstances()) {
            if (inst.getInstanceName().equals(name))
                instance = Optional.of(inst);
        }
        return instance;
    }

    /**
     * Creates a unique name for an instance based on the given baseName
     * @param network the network of instances
     * @param baseName the baseName for the instance, if there are already uses of this baseName, baneName_i is
     *                 returned
     * @return a unique name
     */
    private String uniquePlinkName(Network network, String baseName) {
        Set<String> names = network.getInstances().stream().map(Instance::getInstanceName)
                .filter(n -> n.equals(baseName))
                .collect(Collectors.toSet());
        return baseName + "_" + names.size();

    }
}
