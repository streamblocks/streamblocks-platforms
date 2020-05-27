package ch.epfl.vlsc.hls.backend.systemc;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;

import ch.epfl.vlsc.platformutils.utils.Box;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
import org.multij.BindingKind;
import org.multij.Binding;
import org.multij.Module;

import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Module
public interface SystemCNetwork {


    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }


    @Binding(BindingKind.LAZY)
    default Map<Instance, SCInstance> instances() {
        return new HashMap<Instance, SCInstance>();
    }

    @Binding(BindingKind.LAZY)
    default Map<Connection, Queue> queues() {
        return new HashMap<Connection, Queue>();
    }

    @Binding(BindingKind.LAZY)
    default Box<SCNetwork> network() {
        return Box.empty();
    }

    default SCNetwork createSCNetwork(Network network) {
        ImmutableList.Builder<SCNetwork.InputIF> inputs = ImmutableList.builder();
        for (PortDecl inPort : network.getInputPorts()) {

            Connection connection = network.getConnections()
                    .stream()
                    .filter(c ->
                            !c.getSource().getInstance().isPresent() &&
                                    c.getSource().getPort().equals(inPort.getName()))
                    .findAny().orElseThrow(
                            () -> new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not find connection for" +
                                            " network input port " + inPort.getName())));

            Queue queue = findQueue(connection);
            String prefix = inPort.getName() + "_";
            inputs.add(SCNetwork.InputIF.of(queue, prefix));
        }
        ImmutableList.Builder<SCNetwork.OutputIF> outputs = ImmutableList.builder();
        for (PortDecl outPort : network.getOutputPorts()) {

            Connection connection = network.getConnections()
                    .stream()
                    .filter(c ->
                            !c.getTarget().getInstance().isPresent() &&
                                    c.getTarget().getPort().equals(outPort.getName()))
                    .findAny().orElseThrow(
                            () -> new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not find connection for" +
                                            " network output port " + outPort.getName())));
            Queue queue = findQueue(connection);
            String prefix = outPort.getName() + "_";


            outputs.add(SCNetwork.OutputIF.of(queue, prefix));
        }
        ImmutableList<SCInstance> instances = network.getInstances().map(this::findSCInstance);
        ImmutableList<Queue> queues = network.getConnections().map(this::findQueue);

        String identifier = backend().task().getIdentifier().getLast().toString();
        SCNetwork scNetwork = new SCNetwork(identifier, inputs.build(), outputs.build(), instances, queues);
        return scNetwork;
    }

    default Connection findInputPortConnection(Instance instance, PortDecl inPort) {
        String instName = instance.getInstanceName();
        String portName = inPort.getName();
        Connection.End target = new Connection.End(Optional.of(instName), portName);
        Connection connection = backend()
                .task().getNetwork().getConnections().stream()
                .filter(c -> c.getTarget().equals(target)).findAny()
                .orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(Diagnostic.Kind.ERROR,
                                        String.format("Could not find the connection for target %s.%s",
                                                target.getInstance().get(), portName))));
        return connection;
    }

    default Connection findOutputPortConnection(Instance instance, PortDecl outPort) {
        String instName = instance.getInstanceName();
        String portName = outPort.getName();
        Connection.End source = new Connection.End(Optional.of(instName), portName);
        Connection connection = backend()
                .task().getNetwork().getConnections().stream()
                .filter(c -> c.getSource().equals(source)).findAny()
                .orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(Diagnostic.Kind.ERROR,
                                        String.format("Could not find the connection for source %s.%s",
                                                source.getInstance().get(), portName))));
        return connection;
    }

    default SCInstance findSCInstance(Instance instance) {

        if (!instances().containsKey(instance)) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            Entity entity = entityDecl.getEntity();
            ImmutableList.Builder<SCInstance.InputIF> readers = ImmutableList.builder();
            for (PortDecl inPort : entity.getInputPorts()) {
                Connection connection = findInputPortConnection(instance, inPort);
                String prefix = inPort.getName() + "_V_";
                Queue queue = findQueue(connection);
                readers.add(SCInstance.InputIF.of(queue, prefix));
            }

            ImmutableList.Builder<SCInstance.OutputIF> writers = ImmutableList.builder();
            for (PortDecl outPort : entity.getOutputPorts()) {
                Connection connection = findOutputPortConnection(instance, outPort);
                String prefix = outPort.getName() + "_V_";
                Queue queue = findQueue(connection);
                writers.add(SCInstance.OutputIF.of(queue, prefix));
            }

            String name = backend().instaceQID(instance.getInstanceName(), "_");
            SCInstance newInst = new SCInstance(name, readers.build(), writers.build());
            instances().put(instance, newInst);
            return newInst;
        } else {
            return instances().get(instance);
        }

    }

    default Queue findQueue(Connection connection) {
        if (queues().containsKey(connection)) {
            return queues().get(connection);
        } else {
            int bitWidth = backend().typeseval().sizeOfBits(getConnectionType(connection));
            int bufferDepth = backend().channelsutils().connectionBufferSize(connection);
            Queue queue = new Queue(connection, bitWidth, bufferDepth);
            queues().put(connection, queue);
            return queue;
        }
    }


    default Type getConnectionType(Connection connection) {
        Network network = backend().task().getNetwork();
        Connection.End source = connection.getSource();
        Connection.End target = connection.getTarget();
        if (source.getInstance().isPresent() && target.getInstance().isPresent()) {
            Instance sourceInst = network.getInstances()
                    .stream()
                    .filter(inst ->
                            inst.getInstanceName().equals(source.getInstance().get()))
                    .findAny()
                    .orElseThrow(
                            () -> new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not find port for " +
                                            "connection " + source.getInstance().get() + "." + source.getPort())));
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(sourceInst.getEntityName(), true);
            PortDecl port = entityDecl.getEntity().getOutputPorts().stream()
                    .filter(p -> p.getName().equals(source.getPort()))
                    .findAny().orElseThrow(
                            () -> new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                            String.format("Could not find port %s on instance %s", source.getPort(),
                                                    sourceInst.getInstanceName()))));

            return backend().types().declaredPortType(port);

        } else if (source.getInstance().isPresent() && !target.getInstance().isPresent()) {

            PortDecl port = network.getOutputPorts().stream()
                    .filter(p -> p.getName().equals(target.getPort()))
                    .findAny()
                    .orElseThrow(
                            () -> new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                            String.format(
                                                    "Could not find output port %s for connection %s.%s -> %1$s",
                                                    source.getInstance().get(), source.getPort(), target.getPort()))));
            return backend().types().declaredPortType(port);

        } else if (!source.getInstance().isPresent() && target.getInstance().isPresent()) {
            PortDecl port = network.getOutputPorts().stream()
                    .filter(p -> p.getName().equals(source.getPort()))
                    .findAny()
                    .orElseThrow(
                            () -> new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                            String.format(
                                                    "Could not find input port %s for connection $1s -> %s.%s",
                                                    source.getPort(), target.getInstance().get(), target.getPort()))));
            return backend().types().declaredPortType(port);

        } else {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            String.format(".%s -> .%s is a stranded connection!",
                                    source.getPort(), target.getPort())));
        }

    }


    default void generateNetwork() {



        Network network = backend().task().getNetwork();

        SCNetwork scNetwork = createSCNetwork(network);

        String identifier = scNetwork.getIdentifier();

        network().set(scNetwork);

        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(identifier + ".cpp"));

        if (!backend().externalMemory().externalMemories().isEmpty()) {
            backend().context().getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, "External memories are not yet supported on" +
                            "SystemC networks!")
            );
        }


        // -- Include headers
        getIncludes(scNetwork);

        emitter().emitNewLine();

        emitter().emit("class %s: public sc_module {", identifier);
        {

            emitter().emit("public:");
            emitter().increaseIndentation();
            // -- get io signals
            getModulePorts(scNetwork);

            // -- get internal signals
            getInternalSignals(scNetwork);

            // -- get instances
            getInstances(scNetwork);

            // -- get fifos
            getQueues(scNetwork);

            // -- get triggers
            getTriggers(scNetwork);

            // -- get process methods

            getSyncSignals(scNetwork);
            getWaitedSignals(scNetwork);
            getGlobalSyncSignals(scNetwork);
            getAmIdle(scNetwork);
            getApIdle(scNetwork);

            // -- get constructor
            getConstructor(scNetwork);

            emitter().decreaseIndentation();
        }
        emitter().emit("}; // class %s", identifier);
        emitter().emitNewLine();

        network().clear();
        emitter().close();
    }


    default void getIncludes(SCNetwork network) {

        emitter().emit("#include \"systemc.h\"");
        emitter().emit("#include \"trigger.h\"");
        emitter().emit("#include \"queue.h\"");
        network.getInstances().stream().forEach(this::getIncludeInstance);
        emitter().emitNewLine();
    }

    default void getIncludeInstance(SCInstance instance) {
        emitter().emit("#include \"%s\"", instance.getName());
    }

    default void getModulePorts(SCNetwork network) {
        // -- network ports
        emitter().emit("// -- Network ports");
        network.stream().forEach(this::getModulePort);
        emitter().emitNewLine();
    }

    default void getModulePort(PortIF port) {

        boolean isOutput = port.getKind().orElseThrow(
                () -> new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                String.format("Attempting to query port kind %s when it was not set!",
                                        port.getName())))) == PortIF.Kind.OUTPUT;
        emitter().emit("%s <%s> %s;", isOutput ? "sc_out" : "sc_in", port.getSignal().getType(), port.getName());
    }
    default void getInternalSignals(SCNetwork network) {

        emitter().emit("// -- internal signals");
        network.getInternalSignals().forEach(this::declareSignal);

        network.getInstances().stream().forEach(this::getInstanceSignals);

        network.getTriggers().stream().forEach(this::getTriggerSignals);

    }

    default void getInstanceSignals(SCInstance instance) {


        emitter().emit("// -- Signals for instance %s", instance.getName());
        instance.stream().map(PortIF::getSignal).forEach(this::declareSignal);

    }

    default void getTriggerSignals(SCTrigger trigger) {

        emitter().emit("// -- Signals for trigger %s", trigger.getName());
        trigger.streamUnique().map(PortIF::getSignal).forEach(this::declareSignal);
    }
    default void declareSignal(Signal signal) {
        if (!(signal.getName().equals("ap_clk") || signal.getName().equals("rst_n")))
            emitter().emit("sc_signal<%s> %s;", signal.getType(), signal.getName());
    }

    default void getInstances(SCNetwork network) {
        emitter().emit("// -- instances");
        network.getInstances()
                .stream()
                .forEach(inst ->
                        emitter().emit("%s *%s;", inst.getName(), inst.getInstanceName()));
        emitter().emitNewLine();
    }


    default void getQueues(SCNetwork network) {

        emitter().emit("// -- Queues");
        for (Queue queue : network.getQueues()) {

            int addressWidth = MathUtils.log2Ceil(queue.getDepth());
            int width = queue.getType().getWidth();
            emitter().emit("Queue<%d, %d> *%s;", width, addressWidth, queue.getName());
        }
        emitter().emitNewLine();

    }

    default void getTriggers(SCNetwork network) {
        emitter().emit("// -- Triggers");
        for (SCTrigger trigger : network.getTriggers()) {
            emitter().emit("Trigger *%s;", trigger.getName());
        }
    }

    default void getConstructor(SCNetwork network) {


        // -- The constructor
        emitter().emit("// -- constructor");

        emitter().emit("%s(sc_module_name name):", network.getIdentifier());
        {
            emitter().increaseIndentation();
            {
                // -- construct sub modules
                emitter().increaseIndentation();
                network.getInstances().stream().forEach(inst ->
                    emitter().emit("%s (\"%1$s\"),", inst.getInstanceName())
                );
                emitter().emit("sc_module(name) {");
                emitter().decreaseIndentation();
            }
            // -- connect submodules
            emitter().emit("// -- hardware actor port bindings");
            network.getInstances().stream().forEach(
                inst-> {
                    emitter().emit("// --port bindings for %s", inst.getName());
                    inst.stream().forEach(port ->
                            emitter().emit("%s.%s(%s);", inst.getInstanceName(),
                                    port.getName(), port.getSignal().getName()));
                    emitter().emitNewLine();

                });

            emitter().emitNewLine();
            // -- connect queues
            emitter().emit("// -- hardware queues port bindings");
            network.getQueues().stream().forEach(
                queue -> {
                    emitter().emit("// -- port bindings for queue %s", queue.getName());
                    queue.stream().forEach(port ->
                            emitter().emit("%s.%s(%s);", queue.getName(), port.getName(),
                                    port.getSignal().getName()));
                    emitter().emitNewLine();
                });
            // -- connect triggers
            emitter().emit("// -- hardware trigger port bindings");
            network.getTriggers().stream().forEach(
                  trigger -> {
                      emitter().emit("// -- port binding for trigger %s", trigger.getName());
                      trigger.stream().forEach(port ->
                              emitter().emit("%s.%s(%s);", trigger.getName(), port.getName(),
                                      port.getSignal().getNameRanged()));
                  }
            );
            emitter().emitNewLine();

            // -- Register methods
            List<Signal> syncExecs = network.getTriggers().stream().map(SCTrigger::getSyncExec).map(PortIF::getSignal).collect(Collectors.toList());
            List<Signal> syncWaits = network.getTriggers().stream().map(SCTrigger::getSyncWait).map(PortIF::getSignal).collect(Collectors.toList());
            List<Signal> waited = network.getTriggers().stream().map(SCTrigger::getWaited).map(PortIF::getSignal).collect(Collectors.toList());
            List<Signal> sleeps = network.getTriggers().stream().map(SCTrigger::getSleep).map(PortIF::getSignal).collect(Collectors.toList());
            // -- Method to set the sync signals for each instance
            emitter().emit("SC_METHOD(setSyncSignals)");
            emitter().emit("sensitive << %s;", String.join(" << ",
                    Stream.concat(syncExecs.stream(), syncWaits.stream()).map(Signal::getName).collect(Collectors.toList())));
            emitter().emitNewLine();
            // -- Method to set the waited signals for each instance trigger
            emitter().emit("SC_METHOD(setWaitedSignals)");
            emitter().emit("sensitive << %s;", String.join(" << ",
                    waited.stream().map(Signal::getName).collect(Collectors.toList())));
            emitter().emitNewLine();
            // -- Method to set the sleep signals
            emitter().emit("SC_METHOD(setGlobalSyncSignals);");
            emitter().emit("sensitive << %s;", String.join(" << ",
                    Stream.concat(
                            sleeps.stream(),
                            Stream.concat(
                                    syncWaits.stream(),
                                    network.getInstanceSyncSignals().stream()))
                    .map(Signal::getName).collect(Collectors.toList())));


            // -- Method to set the internal idle reg
            emitter().emit("SC_METHOD(setAmIdle);");
            emitter().emit("sensitive_pos << %s;", network.getApControl().getClockSignal().getName());
            emitter().emitNewLine();

            // -- Method to set the ap idle signal
            emitter().emit("SC_METHOD(setApIdle);");
            emitter().emit("sensitive << %s;",
                    String.join (" << ",
                        network.getTriggers()
                                .map(SCTrigger::getApControl)
                                .map(APControl::getIdleSignal)
                                .map(Signal::getName)));

            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");


    }

    default void getSyncSignals(SCNetwork network) {

        emitter().emit("void setSyncSignals() {");
        {
            emitter().increaseIndentation();
            network.getTriggers().stream().forEach(trigger -> {
                emitter().emit("// -- sync assignment for %s", trigger.getName());
                Signal syncExec = trigger.getSyncExec().getSignal();
                Signal syncWait = trigger.getSyncWait().getSignal();
                int ix = network.getTriggers().indexOf(trigger);
                Signal sync = network.getInstanceSyncSignals().get(ix);
                emitter().emit("%s = %s | %s;", sync.getName(), syncExec.getName(), syncWait.getName());
            });
            emitter().emitNewLine();

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void getWaitedSignals(SCNetwork network) {

        emitter().emit("void setWaitedSignals() {");
        {
            emitter().increaseIndentation();
            network.getTriggers().stream().forEach(trigger -> {
                Signal waited = trigger.getWaited().getSignal();
                Stream<Signal> othersWaited = network.getTriggers().stream()
                        .filter(t -> !t.equals(trigger))
                        .map(SCTrigger::getWaited)
                        .map(PortIF::getSignal);
                emitter().emit("%s = %s;", waited.getName(),
                        String.join("& ", othersWaited.map(Signal::getName).collect(Collectors.toList())));

            });

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void getGlobalSyncSignals(SCNetwork network) {

        emitter().emit("void setGlobalSyncSignals() {");
        {
            emitter().increaseIndentation();

            // -- external enqueue
            emitter().emit("%s = SC_LOGIC_0;",
                    network.getGlobalSync().getExternalEnqueue().getSignal().getName());
            emitter().emitNewLine();

            // -- all sleep
            emitter().emit("%s = %s;", network.getGlobalSync().getAllSleep().getSignal().getName(),
                    String.join(" & ",
                            network.getTriggers().stream()
                                    .map(SCTrigger::getSleep)
                                    .map(PortIF::getSignal)
                                    .map(Signal::getName)
                                    .collect(Collectors.toList())));
            emitter().emitNewLine();
            // -- all sync wait
            emitter().emit("%s = %s;",
                    network.getGlobalSync().getAllSyncWait().getSignal().getName(),
                    String.join(" & ",
                            network.getTriggers().stream()
                                    .map(SCTrigger::getSyncWait)
                                    .map(PortIF::getSignal)
                                    .map(Signal::getName)
                                    .collect(Collectors.toList())));
            emitter().emitNewLine();
            // -- all sync
            emitter().emit("%s = %s;",
                    network.getGlobalSync().getAllSync().getSignal().getName(),
                    String.join(" & ",
                            network.getInstanceSyncSignals().stream()
                                    .map(Signal::getName)
                                    .collect(Collectors.toList())));

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getAmIdle(SCNetwork network) {

        emitter().emit("void setAmIdle() {");
        {
            emitter().increaseIndentation();

            emitter().emit("if (%s == SC_LOGIC_0)", network.getApControl().getResetSignal());
            {
                emitter().increaseIndentation();
                emitter().emit("%s = SC_LOGIC_0;", network.getAmIdleReg().getName());
                emitter().decreaseIndentation();
            }
            emitter().emit("else");
            {
                emitter().increaseIndentation();
                emitter().emit("%s = %s;", network.getAmIdleReg().getName(), network.getAmIdle().getName());
                emitter().decreaseIndentation();
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void getApIdle(SCNetwork network) {
        emitter().emit("void setApIdle()");
        emitter().emitNewLine();
        {
            emitter().increaseIndentation();
            emitter().emit("sc_logic is_idle = %s;",
                    String.join(" & ",
                            network.getTriggers()
                                    .map(SCTrigger::getApControl)
                                    .map(APControl::getIdleSignal)
                                    .map(Signal::getName)));
            emitter().emitNewLine();
            emitter().emit("%s = is_idle;", network.getAmIdle().getName());
            emitter().emit("%s = is_idle & (~%s);",
                    network.getApControl().getDoneSignal().getName(),
                    network.getAmIdleReg().getName());
            emitter().emit("%s = is_idle;", network.getApControl().getIdleSignal().getName());
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

}