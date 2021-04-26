package ch.epfl.vlsc.hls.backend.systemc;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.hls.backend.directives.Directives;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;

import ch.epfl.vlsc.platformutils.utils.Box;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.BindingKind;
import org.multij.Binding;
import org.multij.Module;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.ListType;
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
        PortIF initPort = PortIF.of(
                "kernel_start",
                Signal.of("kernel_start", new LogicValue()),
                Optional.of(PortIF.Kind.INPUT));

        ImmutableList.Builder<SCInputStage> inputs = ImmutableList.builder();
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
            String inputStageInstanceName = "input_stage_" + queue.getName();
            SCInputStage.InputIF fifoIf = new SCInputStage.InputIF(queue, inPort);
            String underlyingType = backend().typeseval().type(backend().types().declaredPortType(inPort));
            inputs.add(new SCInputStage(inputStageInstanceName, initPort, fifoIf, underlyingType));


        }
        ImmutableList.Builder<SCOutputStage> outputs = ImmutableList.builder();
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

            String outputStageInstanceName = "output_stage_" + queue.getName();
            SCOutputStage.OutputIF fifoIf = new SCOutputStage.OutputIF(queue, outPort);
            String underlyingType = backend().typeseval().type(backend().types().declaredPortType(outPort));
            outputs.add(
                    new SCOutputStage(outputStageInstanceName, initPort, fifoIf, underlyingType));

        }
        ImmutableList<SCInstance> instances = network.getInstances().map(this::findSCInstance);
        ImmutableList<Queue> queues = network.getConnections().map(this::findQueue);

        String identifier = backend().task().getIdentifier().getLast().toString();
        SCNetwork scNetwork = new SCNetwork(identifier, inputs.build(), outputs.build(), instances, queues, initPort);
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
                String prefix2 = "io_" + inPort.getName() + "_";
                Queue queue = findQueue(connection);
                readers.add(new SCInstance.InputIF(queue, prefix, prefix2));
            }

            ImmutableList.Builder<SCInstance.OutputIF> writers = ImmutableList.builder();
            for (PortDecl outPort : entity.getOutputPorts()) {
                Connection connection = findOutputPortConnection(instance, outPort);
                String prefix = outPort.getName() + "_V_";
                String prefix2 = "io_" + outPort.getName() + "_";
                Queue queue = findQueue(connection);
                writers.add(new SCInstance.OutputIF(queue, prefix, prefix2));
            }
            ActorMachine am = (ActorMachine) entity;

            String name = instance.getInstanceName();

            ImmutableList<String> actionIds = am.getTransitions().map(transition -> {
                Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());

                if (annotation.isPresent()) {
                    return ((ExprLiteral) annotation.get().getParameters().get(0).getExpression()).getText();
                } else {
                    throw new CompilationException(
                            new Diagnostic(Diagnostic.Kind.ERROR, String.format("" +
                                            "ActionId annotation missing for actor %s ",
                                    instance.getEntityName().toString())));

                }
            });
            Boolean pipelined =
                    !backend().context().getConfiguration().get(PlatformSettings.disablePipelining)
                    && entity.getAnnotations().stream().anyMatch(annon ->
                    Directives.directive(annon.getName()) == Directives.PIPELINE);
            SCInstance newInst = new SCInstance(name, readers.build(), writers.build(), actionIds, pipelined);
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
            PortDecl port = network.getInputPorts().stream()
                    .filter(p -> p.getName().equals(source.getPort()))
                    .findAny()
                    .orElseThrow(
                            () -> new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                            String.format(
                                                    "Could not find input port %s for connection %1$s -> %s.%s",
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
        String headerId = "__" + identifier.replace("-", "_") + "__";

        network().set(scNetwork);

        emitter().open(PathUtils.getTarget(backend().context()).resolve("systemc/include/" + identifier + ".h"));

        if (!backend().externalMemory().getExternalMemories(network).isEmpty()) {


            backend().context().getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            String.format(
                                    "Actors with external memories for SystemC are not yet supported, consider increasing" +
                                            " the on-chip memory using --set max-bram=VALUE_BYTES to " +
                                            "make all memories internal")));


        }

        emitter().emit("#ifndef __%s_H__", headerId);
        emitter().emit("#define __%s_H__", headerId);
        emitter().emitNewLine();
        // -- Include headers
        getIncludes(scNetwork);

        emitter().emitNewLine();
        emitter().emit("namespace ap_rtl {");
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


            getAllWaitedSignalSetter(scNetwork);
            getAllSleepSignalSetter(scNetwork);
            getAllSyncSleepSignalSetter(scNetwork);

            getAmIdle(scNetwork);
            getApIdle(scNetwork);

            // -- get constructor
            getConstructor(scNetwork);

            emitter().decreaseIndentation();
        }
        emitter().emit("}; // class %s", identifier);
        emitter().emitNewLine();

        emitter().emitNewLine();


        emitter().emitNewLine();

        emitter().emit("} // namespace ap_rtl");
        emitter().emitNewLine();

        emitter().emit("#endif // __%s_H__", headerId);
        network().clear();
        emitter().close();
    }


    default void getIncludes(SCNetwork network) {

        emitter().emit("#include <memory>");
        emitter().emit("#include \"systemc.h\"");
        emitter().emit("#include \"trigger.h\"");
        emitter().emit("#include \"queue.h\"");
        emitter().emit("#include \"simulation-iostage.h\"");
        network.getInstances().stream().forEach(this::getIncludeInstance);
        emitter().emitNewLine();
    }

    default void getIncludeInstance(SCInstance instance) {
        emitter().emit("#include \"%s.h\" // generated by Verilator", instance.getName());
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
        emitter().emit("%s <%s> %s;", isOutput ? "sc_out" : "sc_in", port.getSignal().getType(), port.getSignal().getName());
    }

    default void getInternalSignals(SCNetwork network) {

        emitter().emit("// -- internal signals");
        network.getInternalSignals().forEach(this::declareSignal);
        emitter().emit("// -- instance signals");
        network.getInstances().stream().forEach(this::getInstanceSignals);
        emitter().emit("// -- input stage signals");
        network.getInputStages().stream().forEach(this::getInstanceSignals);
        emitter().emit("// -- output stage signals");
        network.getOutputStages().stream().forEach(this::getInstanceSignals);
        emitter().emit("// -- queue signals");
        network.getQueues().stream().forEach(this::getQueueSignals);
        emitter().emit("// -- instance trigger signals");
        network.getInstanceTriggers().stream().forEach(this::getTriggerSignals);
        emitter().emit("// -- input stage trigger signals");
        network.getInputStageTriggers().stream().forEach(this::getTriggerSignals);
        emitter().emit("// -- output stage trigger signals ");
        network.getOutputStageTriggers().stream().forEach(this::getTriggerSignals);


    }

    default void getInstanceSignals(SCInstanceIF instance) {


        emitter().emit("// -- Signals for instance %s", instance.getInstanceName());
        instance.streamUnique().map(PortIF::getSignal).forEach(this::declareSignal);

    }

    default void getQueueSignals(Queue queue) {

        emitter().emit("// -- signals for queue %s", queue.getName());
        queue.streamUnique().map(PortIF::getSignal).forEach(this::declareSignal);

    }

    default void getTriggerSignals(SCTrigger trigger) {

        emitter().emit("// -- Signals for trigger %s", trigger.getName());
        trigger.streamUnique().map(PortIF::getSignal).forEach(this::declareSignal);
    }

    default void declareSignal(Signal signal) {

        emitter().emit("sc_signal<%s> %s;", signal.getType(), signal.getName());
    }

    default void getInstances(SCNetwork network) {
        emitter().emit("// -- instances");
        network.getInstances().stream().forEach(this::defineInstanceObject);
        emitter().emitNewLine();
        emitter().emit("// -- input stages");
        network.getInputStages().stream().forEach(this::defineInstanceObject);
        emitter().emitNewLine();
        emitter().emit("// -- output stages");
        network.getOutputStages().stream().forEach(this::defineInstanceObject);
        emitter().emitNewLine();

    }

    default void defineInstanceObject(SCInstanceIF instance) {
        emitter().emit("std::unique_ptr<%s> %s;", instance.getName(), instance.getInstanceName());
    }

    default void getQueues(SCNetwork network) {

        emitter().emit("// -- Queues");
        for (Queue queue : network.getQueues()) {

            emitter().emit("std::unique_ptr<Queue<%s>> %s;", queue.getType().getType(), queue.getName());
        }
        emitter().emitNewLine();

    }

    default void getTriggers(SCNetwork network) {

        emitter().emit("// -- Instance Triggers");
        network.getInstanceTriggers().forEach(this::defineTriggerObject);
        emitter().emitNewLine();
        emitter().emit("// -- Input stage Triggers");
        network.getInputStageTriggers().forEach(this::defineTriggerObject);
        emitter().emitNewLine();
        emitter().emit("// -- Output stage Triggers");
        network.getOutputStageTriggers().forEach(this::defineTriggerObject);
        emitter().emitNewLine();

    }

    default void defineTriggerObject(SCTrigger trigger) {
        emitter().emit("std::unique_ptr<%s> %s;", trigger.getTriggerClass(), trigger.getName());
    }

    default void getConstructor(SCNetwork network) {


        // -- The constructor
        emitter().emit("SC_HAS_PROCESS(%s);", network.getIdentifier());
        emitter().emit("// -- constructor");

        emitter().emit("%s(sc_module_name name, bool enable_profiler=false, unsigned int queue_capacity=512):", network.getIdentifier());
        {
            emitter().increaseIndentation();
            {
                // -- construct sub modules
                emitter().increaseIndentation();
                emitter().emit("sc_module(name), ");
                //-- construct instances
                emitter().emit("// -- set names for the ports (useful for debug)");
                List<PortIF> ports = network.stream().collect(Collectors.toList());
                for (PortIF port : ports) {
                    boolean last = ports.indexOf(port) == ports.size() - 1;
                    emitter().emit("%s(\"%1$s\")%s", port.getSignal().getName(), last ? "{" : ",");
                }

                emitter().decreaseIndentation();
            }

            // -- construct modules
            // -- actors
            emitter().emit("// -- instance constructors");
            network.getInstances().forEach(this::constructInstance);
            emitter().emitNewLine();
            emitter().emit("// -- input stage constructors");
            network.getInputStages().forEach(this::constructInstance);
            emitter().emitNewLine();
            emitter().emit("// -- output stage constructors");
            network.getOutputStages().forEach(this::constructInstance);
            emitter().emitNewLine();
            ;

            // -- triggers
            emitter().emit("// -- instance trigger constructors");
            network.getInstanceTriggers().forEach(this::constructTrigger);
            emitter().emit("// -- registers actions for profiling");

            network.getInstances().forEach(inst -> registerActions(inst, network.getInstanceTrigger(inst)));
            emitter().emitNewLine();
            emitter().emit("/// -- input stage trigger constructors");
            network.getInputStageTriggers().forEach(this::constructTrigger);
            emitter().emitNewLine();
            emitter().emit("// -- output stage trigger constructors");
            network.getOutputStageTriggers().forEach(this::constructTrigger);
            emitter().emitNewLine();


            // -- queues
            emitter().emit("// -- queue constructors");
            network.getQueues().stream().forEach(queue -> {
                int addressWidth = MathUtils.log2Ceil(queue.getDepth());
                String bufferCapacity = queue.getDepth() != 0 ? (queue.getDepth() + "") : "queue_capacity";
                emitter().emit("%s = std::make_unique<Queue<%s>>(\"%1$s\", %s);", queue.getName(),
                        queue.getType().getType(), bufferCapacity);
            });
            emitter().emitNewLine();
            // -- connect submodules
            emitter().emit("// -- hardware actor port bindings");
            network.getInstances().stream().forEach(this::bindInstnacePorts);
            emitter().emitNewLine();

            emitter().emit("// -- input stage port bindings");
            network.getInputStages().forEach(this::bindInstnacePorts);
            emitter().emitNewLine();

            emitter().emit("// -- output stage port bindings");
            network.getOutputStages().forEach(this::bindInstnacePorts);
            emitter().emitNewLine();


            // -- connect queues
            emitter().emit("// -- hardware queues port bindings");
            network.getQueues().forEach(q -> bindQueuePorts(q, network));

            // -- connect triggers
            emitter().emit("// -- hardware actor trigger port bindings");
            network.getInstanceTriggers().forEach(this::bindTriggerPorts);
            emitter().emitNewLine();
            emitter().emit("// -- input stage trigger port bindings");
            network.getInputStageTriggers().forEach(this::bindTriggerPorts);
            emitter().emitNewLine();
            emitter().emit("// -- output stage trigger port bindings");
            network.getOutputStageTriggers().forEach(this::bindTriggerPorts);
            emitter().emitNewLine();


            // -- Register methods

            List<Signal> waited = network.getAllTriggers().stream().map(SCTrigger::getWaited).map(PortIF::getSignal).collect(Collectors.toList());
            List<Signal> sleeps = network.getAllTriggers().stream().map(SCTrigger::getSleep).map(PortIF::getSignal).collect(Collectors.toList());
            List<Signal> syncSleeps = network.getAllTriggers().stream().map(SCTrigger::getSyncSleep).map(PortIF::getSignal).collect(Collectors.toList());
            emitter().emitNewLine();
            // -- Method to set the waited signals for each instance trigger
            emitter().emit("SC_METHOD(setAllWaited);");
            emitter().emit("sensitive << %s;", waited.stream()
                    .map(Signal::getName).collect(Collectors.joining(" << ")));
            emitter().emitNewLine();

            // -- Method to set the sleep signals
            emitter().emit("SC_METHOD(setAllSleep);");
            emitter().emit("sensitive << %s;", sleeps.stream().map(Signal::getName).collect(
                    Collectors.joining(" << ")));
            emitter().emitNewLine();

            // -- Method to set the sleep signals
            emitter().emit("SC_METHOD(setAllSyncSleep);");
            emitter().emit("sensitive << %s;", syncSleeps.stream().map(Signal::getName).collect(
                    Collectors.joining(" << ")));
            emitter().emitNewLine();

            // -- Method to set the internal idle reg
            emitter().emit("SC_METHOD(setAmIdle);");
            emitter().emit("sensitive << %s.pos();", network.getApControl().getClockSignal().getName());
            emitter().emitNewLine();

            // -- Method to set the ap idle signal
            emitter().emit("SC_METHOD(setApIdle);");
            emitter().emit("sensitive << %s << %s;", network.getAmIdleReg().getName(),
                    String.join(" << ",
                            network.getAllTriggers()
                                    .map(SCTrigger::getApControl)
                                    .map(APControl::getIdleSignal)
                                    .map(Signal::getName)));

            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().emitNewLine();


    }

    default void constructInstance(SCInstanceIF instance) {
        emitter().emit("%s = std::make_unique<%s>(\"%1$s\");", instance.getInstanceName(), instance.getName());
    }

    default void constructTrigger(SCTrigger trigger) {

        String triggerClass = trigger.getTriggerClass();
        emitter().emit("%s = std::make_unique<%s>(\"%1$s\", \"%s\", enable_profiler);", trigger.getName(), triggerClass,
                trigger.getActorName());
    }

    default void registerActions(SCInstance instance, SCTrigger trigger) {
        if (backend().context().getConfiguration().get(PlatformSettings.enableActionProfile))
            instance.getActionsIds().forEach(id -> {
                int ix = instance.getActionsIds().indexOf(id);
                emitter().emit("%s->registerAction(%d, \"%s\");", trigger.getName(), ix, id);
            });
        else {
            emitter().emit("%s->registerAction(0, \"__all__\");", trigger.getName());
        }
    }

    default void bindInstnacePorts(SCInstanceIF instance) {

        emitter().emit("// --port bindings for %s", instance.getName());
        instance.stream().forEach(port ->
                emitter().emit("%s->%s.bind(%s);", instance.getInstanceName(),
                        port.getName(), port.getSignal().getName()));
        emitter().emitNewLine();

    }

    default void bindTriggerPorts(SCTrigger trigger) {
        emitter().emit("// -- port binding for trigger %s", trigger.getName());
        trigger.stream().forEach(port ->
                emitter().emit("%s->%s.bind(%s);", trigger.getName(), port.getName(),
                        port.getSignal().getName()));
    }

    default void bindQueuePorts(Queue queue, SCNetwork network) {
        emitter().emit("// -- port bindings for queue %s", queue.getName());
        emitter().emit("%s->%s.bind(%s);", queue.getName(), network.getApControl().getClock().getName(), network.getApControl().getClockSignal().getName());
        emitter().emit("%s->%s.bind(%s);", queue.getName(), network.getApControl().getReset().getName(), network.getApControl().getResetSignal().getName());
        queue.stream().forEach(port ->
                emitter().emit("%s->%s.bind(%s);", queue.getName(), port.getName(),
                        port.getSignal().getName()));
        emitter().emitNewLine();
    }


    default void getAllWaitedSignalSetter(SCNetwork network) {

        emitter().emit("void setAllWaited() {");
        {
            emitter().increaseIndentation();
            // -- all waited
            emitter().emit("%s = %s;", network.getGlobalSync().getAllWaited().getSignal().getName(),
                    network.getAllTriggers().stream()
                            .map(SCTrigger::getWaited)
                            .map(PortIF::getSignal)
                            .map(Signal::getName)
                            .collect(Collectors.joining(" & ")));
            emitter().emitNewLine();

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void getAllSleepSignalSetter(SCNetwork network) {

        emitter().emit("void setAllSleep() {");
        {
            emitter().increaseIndentation();


            // -- all sleep
            emitter().emit("%s = %s;", network.getGlobalSync().getAllSleep().getSignal().getName(),
                    network.getAllTriggers().stream()
                            .map(SCTrigger::getSleep)
                            .map(PortIF::getSignal)
                            .map(Signal::getName)
                            .collect(Collectors.joining(" & ")));
            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }
    default void getAllSyncSleepSignalSetter(SCNetwork network) {

        emitter().emit("void setAllSyncSleep() {");
        {
            emitter().increaseIndentation();

            // -- all sync sleep
            emitter().emit("%s = %s;", network.getGlobalSync().getAllSyncSleep().getSignal().getName(),
                    network.getAllTriggers().stream()
                            .map(SCTrigger::getSyncSleep)
                            .map(PortIF::getSignal)
                            .map(Signal::getName)
                            .collect(Collectors.joining(" & ")));
            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getAmIdle(SCNetwork network) {

        emitter().emit("void setAmIdle() {");
        {
            emitter().increaseIndentation();

            emitter().emit("if (%s == %s)", network.getApControl().getResetSignal().getName(),
                    LogicValue.Value.SC_LOGIC_0);
            {
                emitter().increaseIndentation();
                emitter().emit("%s = %s;", network.getAmIdleReg().getName(), LogicValue.Value.SC_LOGIC_1);
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
        emitter().emit("void setApIdle() {");
        emitter().emitNewLine();
        {
            emitter().increaseIndentation();
            Signal is_idle = Signal.of("is_idle", new LogicValue());
            emitter().emit("%s %s = %s;", is_idle.getType(), is_idle.getName(),
                    String.join(" & ",
                            network.getAllTriggers()
                                    .map(SCTrigger::getApControl)
                                    .map(APControl::getIdleSignal)
                                    .map(Signal::getName)));
            emitter().emitNewLine();
            emitter().emit("%s = is_idle;", network.getAmIdle().getName());
            emitter().emit("%s = is_idle & (~%s.read());",
                    network.getApControl().getDoneSignal().getName(),
                    network.getAmIdleReg().getName());
            emitter().emit("%s = is_idle;", network.getApControl().getIdleSignal().getName());
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

}