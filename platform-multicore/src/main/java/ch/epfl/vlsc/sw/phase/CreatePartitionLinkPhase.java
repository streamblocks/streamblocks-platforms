package ch.epfl.vlsc.sw.phase;

import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.compiler.PartitionedCompilationTask;
import ch.epfl.vlsc.phases.ExtractSoftwarePartition;
import ch.epfl.vlsc.phases.NetworkPartitioningPhase;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.backend.ExternalMemory;
import ch.epfl.vlsc.sw.ir.PartitionHandle;
import ch.epfl.vlsc.sw.ir.PartitionLink;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.attribute.VariableScopes;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.compiler.SyntheticSourceUnit;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.ToolAttribute;
import se.lth.cs.tycho.ir.decl.Availability;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.entity.nl.EntityInstanceExpr;
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

import ch.epfl.vlsc.sw.ir.PartitionHandle.Field;
import ch.epfl.vlsc.sw.ir.PartitionHandle.Method;
import ch.epfl.vlsc.sw.ir.PartitionHandle.Type;
import se.lth.cs.tycho.type.ListType;

/**
 * @author Mahyar Emami (mahyar.emami@epfl.ch)
 * @brief creates a PartitionLink entity and instance that is used to generate OpenCL code and stub
 */
public class CreatePartitionLinkPhase implements Phase {


    @Override
    public String getDescription() {
        return "Creates a PartitionLink Entity that directs all floating connections to an instance of this entity";
    }

    CompilationTask task;

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO, "Generating PartitionLink entity."));
        this.task = task;
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

        List<PortDecl> outputPorts = task.getNetwork().getInputPorts().stream().map(PortDecl::deepClone)
                .collect(Collectors.toList());
        // -- get the input ports from connections with missing target
        List<PortDecl> inputPorts = task.getNetwork().getOutputPorts().stream().map(PortDecl::deepClone)
                .collect(Collectors.toList());
        String plinkEntityName = "system_plink";

        // -- create a PartitionLink entity

        ImmutableList.Builder<Memories.InstanceVarDeclPair> memoryResidentVars = ImmutableList.builder();

        if (task instanceof PartitionedCompilationTask &&
            ((PartitionedCompilationTask) task).getPartition(PartitionedCompilationTask.PartitionKind.HW).isPresent()) {
            PartitionedCompilationTask ptask = (PartitionedCompilationTask) task;
            CompilationTask otherTask =
                    task.withNetwork(ptask.getPartition(PartitionedCompilationTask.PartitionKind.HW).get());
            memoryResidentVars.addAll(otherTask.getModule(Memories.key).getExternalMemories(otherTask.getNetwork()));
        }
        PartitionHandle handle = createPartitionHandle(
                ImmutableList.from(inputPorts),
                ImmutableList.from(outputPorts),
                memoryResidentVars.build());


        PartitionLink plink = new PartitionLink(inputPorts, outputPorts, handle);
        GlobalEntityDecl plinkEntity = GlobalEntityDecl.global(Availability.PUBLIC, plinkEntityName, plink, false);
        String plinkInstanceName = uniquePlinkName(task.getNetwork(), plinkEntityName);

        // -- create a PartitionLink instance
        Instance plinkInstance =
                new Instance(plinkInstanceName, QID.of(plinkEntityName), null, null);

        // -- Connect the floating connections to the PartitionLink instance
        List<Connection> attachedConnections =
                Stream.concat(
                        floatingSourceConnections.stream()
                                .map(c ->
                                        c.withSource(new Connection.End(Optional.of(plinkInstanceName),
                                                c.getSource().getPort()))
                                            .withAttributes(c.getAttributes().map(ToolAttribute::deepClone))),
                        floatingTargetConnections.stream()
                                .map(c ->
                                        c.withTarget(new Connection.End(Optional.of(plinkInstanceName),
                                                c.getTarget().getPort()))
                                            .withAttributes(c.getAttributes().map(ToolAttribute::deepClone)))
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
    public static String uniquePlinkName(Network network, String baseName) {
        Set<String> names = network.getInstances().stream().map(Instance::getInstanceName)
                .filter(n -> n.equals(baseName))
                .collect(Collectors.toSet());
        return baseName + "_" + names.size();

    }

    private PartitionHandle createPartitionHandle(ImmutableList<PortDecl> inputPorts,
                                                  ImmutableList<PortDecl> outputPorts,
                                                  ImmutableList<Memories.InstanceVarDeclPair> memories) {
        ImmutableList<PartitionHandle.Method> methods = createMethods(inputPorts, outputPorts);
        ImmutableList<Field> fields = createFields(inputPorts, outputPorts, memories);
        String className = createName();
        PartitionHandle.Method constructor = createConstructor();
        PartitionHandle.Method desttructor = createDestructor();
        return new PartitionHandle(className, methods, fields, constructor, desttructor);
    }

    /**
     * Creates the signature of the PartitionHandle methods, each backend (C or C++) should override this.
     * @return - A list of methods defined and implemented by the PartitionHandle
     */
    private ImmutableList<PartitionHandle.Method> createMethods(ImmutableList<PortDecl> inputPorts,
                                                                ImmutableList<PortDecl> outputPorts) {

        ImmutableList.Builder<Method> funcs = ImmutableList.builder();

        // -- independent methods
        funcs.addAll(
                Method.global("cl_int", "load_file_to_memory",
                        ImmutableList.of(
                                Method.MethodArg("const char*", "filename"),
                                Method.MethodArg("char**", "result"))),

                Method.of("void", "createCLBuffers",
                        ImmutableList.of(
                            Method.MethodArg(Type.of("size_t", true), "cl_write_buffer_size"),
                            Method.MethodArg(Type.of("size_t", true), "cl_read_buffer_size"))),

                Method.of("void", "setArgs"),
                Method.of("void", "enqueueExecution"),
                Method.of("void", "enqueueWriteBuffers"),
                Method.of("void", "enqueueReadSize"),
                Method.of("void", "enqueueReadBuffers"),
                Method.of("void", "waitForReadSize"),
                Method.of("void", "waitForReadBuffers"),
                Method.of("void", "run"),
                Method.of("void", "initEvents"),
                Method.of("void", "releaseMemObjects"),
                Method.of("void", "releaseReadSizeEvents"),
                Method.of("void", "releaseReadBufferEvents"),
                Method.of("void", "releaseKernelEvent"),
                Method.of("void", "releaseWriteEvents"),
                Method.of("void", "freeEvents")
        );

        // -- topology dependent methods



        // -- set request size
        inputPorts.stream().forEachOrdered(
                p -> funcs.add(
                        Method.of("void", "set_" + p.getName() +"_request_size",
                                ImmutableList.of(Method.MethodArg("uint32_t", "req_sz")))));
        // -- set pointers
        Stream.concat(inputPorts.stream(), outputPorts.stream()).forEachOrdered(
                p -> funcs.addAll(
                        Method.of("void", "set_" + p.getName() + "_buffer_ptr",
                                ImmutableList.of(Method.MethodArg(Type.of(p, true), "ptr"))),
                        Method.of("void", "set_" + p.getName() + "_size_ptr",
                                ImmutableList.of(Method.MethodArg(Type.of("uint32_t", true), "ptr")))));
        // -- get pointers
        Stream.concat(inputPorts.stream(), outputPorts.stream()).forEachOrdered(
                p -> funcs.addAll(
                        Method.of(Type.of(p, true), "get_" + p.getName() + "_buffer_ptr"),
                        Method.of(Type.of("uint32_t", true), "get_" + p.getName() + "_size_ptr")));

        return funcs.build();
    }

    /**
     * Creates the a name for the PartitionHandle class, each backend should override this separately.
     * @return name of the class
     */
    private String createName() {
        return "DeviceHandle";
    }

    /**
     * Creates the list of fields in the PartitionHandle class.
     * @return
     */
    private ImmutableList<Field> createFields(ImmutableList<PortDecl> inputPorts,
                                              ImmutableList<PortDecl> outputPorts,
                                              ImmutableList<Memories.InstanceVarDeclPair> memories) {

        ImmutableList.Builder<Field> fields = ImmutableList.builder();

        fields.addAll(
            Field.of("OCLWorld", "world"),
            Field.of("cl_program", "program"),
            Field.of("cl_kernel", "kernel"),
            Field.of("size_t", "global"),
            Field.of("size_t", "local"),
            Field.of("uint32_t", "num_inputs"),
            Field.of("uint32_t", "num_outputs"),
            Field.of("size_t", "buffer_size"),
            Field.of("size_t", "mem_alignment"),
            Field.of("uint64_t", "kernel_command", "the kernel command word (deprecated)"),
            Field.of("uint32_t", "command_is_set", "the kernel command status (deprecated)"),
            Field.of("uint32_t", "pending_status", "status of a kernel run (deprecated)"));
        if (inputPorts.size() > 0) {
            fields.addAll(
                    Field.of(
                            Type.of("cl_event", true),
                            "write_buffer_event",
                            "an array containing write buffer events"),
                    Field.of(
                            Type.of("EventInfo", true),
                            "write_buffer_event_info", "write buffer event info")
            );
        }

        if (inputPorts.size() + outputPorts.size() > 0) {
            fields.addAll(
                    Field.of(
                            Type.of("cl_event", true),
                            "read_size_event",
                            "an array containing read size events"),
                    Field.of(
                            Type.of("EventInfo", true),
                            "read_size_event_info", "read size event info")
            );
        }

        if (outputPorts.size() > 0) {
            fields.addAll(
                    Field.of(
                            Type.of("cl_event", true),
                            "read_buffer_event",
                            "an array containing read buffer events"),
                    Field.of(Type.of("EventInfo", true),
                            "read_buffer_event_info", "read buffer event info")
            );
        }


        fields.addAll(
            Field.of("cl_event",
                    "kernel_event",
                    "kernel enqueue event"),

            Field.of(
                    Type.of("EventInfo", false),
                    "kernel_event_info", "kernel enqueue event info")
        );

        for (PortDecl port: inputPorts) {
            fields.add(
                    Field.of(
                            "uint32_t",
                            port.getName() + "_request_size",
                            "Size of transfer for " + port.getName()));
        }


        ImmutableList.concat(inputPorts, outputPorts)
                .forEach(p -> fields.addAll(
                        Field.of(
                                Type.of(p, true),
                                p.getName() + "_buffer",
                                "host buffer for port " + p.getName()),
                        Field.of(
                                Type.of("uint32_t", true),
                                p.getName() + "_size",
                                "host size buffer for port " + p.getName()),
                        Field.of(
                                "cl_mem",
                                p.getName() + "_cl_buffer",
                                "device buffer for port " + p.getName()
                        ),
                        Field.of("size_t",
                                p.getName() + "_cl_buffer_alloc_size",
                                "allocated size for the cl buffer of port " + p.getName()),
                        Field.of(
                                "cl_mem",
                                p.getName() + "_cl_size",
                                "device size buffer for port " + p.getName())));

        // -- external memories
        memories.forEach(p ->
                fields.add(
                        Field.of("cl_mem",
                                task.getModule(Memories.key).namePair(p) + "_cl_buffer",
                                "cl_buffer descriptor for externally stored variable " +
                                p.getDecl().getName() + " in instance " + p.getInstance().getInstanceName())));

        return fields.build();
    }

    /**
     * Creates a method for the constructor
     * @return
     */
    private PartitionHandle.Method createConstructor() {
        return Method.of("void", "constructor",
                ImmutableList.of(
                        Method.MethodArg("char*", "kernel_name"),
                        Method.MethodArg("char*", "target_device_name"),
                        Method.MethodArg("char*", "dir"),
                        Method.MethodArg("bool", "hw_emu")));
    }

    /**
     * Creates a method for the destructor
     * @return
     */
    private PartitionHandle.Method createDestructor() {
        return Method.of("void", "terminate");
    }



}
