package ch.epfl.vlsc.sw.backend;


import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.ir.PartitionHandle;
import ch.epfl.vlsc.sw.ir.PartitionLink;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.nl.EntityInstanceExpr;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;

import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;
import sun.reflect.generics.reflectiveObjects.GenericArrayTypeImpl;


import java.nio.file.Path;

import java.util.Optional;
import java.util.stream.Collectors;



/**
 * An interface to generate the C code for the PartitionLink instance in a heterogeneous network.
 */
@Module
public interface PLink {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default Types types() {
        return backend().types();
    }

    default void unimpl() {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "plink " +
                stackTrace[2].getMethodName() + " method not implemented!"));
    }

    default TypesEvaluator typeseval() {
        return backend().typesEval();
    }

    default String TxName() {
        return "TX";
    }

    default String RxName() {
        return "RX";
    }

    default String defaultIntType() {
        return "uint32_t";
    }

    default String defaultSizeType() {
        return "size_t";
    }

    default boolean isSimulated() {
        boolean simulated = backend().context().getConfiguration().isDefined(PlatformSettings.enableSystemC) &&
                backend().context().getConfiguration().get(PlatformSettings.enableSystemC);
        return simulated;
    }


    default void generatePLink(Instance instance) throws CompilationException {

        // -- Add the instance to instancebox for later use
        backend().instancebox().set(instance);

        // -- Get the PartitionLink Entity
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add the entity to the entitybox for later use
        backend().entitybox().set(entity);

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        Path instanceTarget;
        // -- Target file Path
        if (backend().context().getConfiguration().get(PlatformSettings.runOnNode)) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Node platform heterogeneous code execution is not" +
                            "supported yet"));
        } else {
            instanceTarget = PathUtils.getTargetCodeGenSource(backend().context())
                    .resolve(instance.getInstanceName() + ".cc");
        }

        emitter().open(instanceTarget);

        // -- Includes
        defineIncludes();

        // -- Ports IO
        portsIO(entity);

        // -- Context
        actionContext(instanceName, entity);


        // -- State
        instanceState(instanceName, entity);

        // -- Prototypes
        prototypes(instanceName, entity);

        // -- Port description
        portDescription(instanceName, entity);


        // -- setParam
        if (isSimulated())
            getSetParam(instanceName, entity);

        // -- State variable description
        stateVariableDescription(instanceName, entity);

        // -- Transitions descriptions
        transitionDescription(instanceName, entity);

        // -- Conditions description
        conditionDescription(instanceName, entity);

        // -- ART ActorClass
        actorClass(instanceName, entity);

        // -- Constructor
        constructorDefinition(instanceName, entity);

        // -- Conditions
        conditionDefinitions(instanceName, entity);

        // -- Actions
        actionDefinitions(instanceName, entity);

        // -- Destructor
        destructorDefinition(instanceName, entity);

        // -- Scheduler FSM
        scheduler(instanceName, entity);

        // -- EOF
        emitter().close();

        // -- clear boxes
        backend().instancebox().clear();
        backend().entitybox().clear();
    }


    default void defineIncludes() {
        backend().instance().defineIncludes();
        if (isSimulated()) {
            emitter().emit("#include <memory>");
            emitter().emit("#include \"network_tester.h\"");
        } else {
            emitter().emit("#include <memory>");
            emitter().emit("#include \"device-handle.h\"");
        }
        emitter().emit("#define MIN(a, b) (a < b ? a : b)");
    }

    default void portsIO(Entity entity) {
        backend().instance().portsIO(entity);

    }

    default void actionContext(String name, Entity entity) {
        // -- ART Context
        emitter().emit("// -- Action Context structure");
        emitter().emit("ART_ACTION_CONTEXT(%d, %d)",
                entity.getInputPorts().size(), entity.getOutputPorts().size());
        emitter().emitNewLine();
    }

    default void simulationState(String name, Entity entity) {

        emitter().emit("// -- simulation handle");
        emitter().emit("std::unique_ptr<ap_rtl::NetworkTester> dev;");


        // -- device object

        // -- input ports
        emitter().emit("std::vector<ap_rtl::PLinkPort> input_ports;");

        // -- output ports
        emitter().emit("std::vector<ap_rtl::PLinkPort> output_ports;");


        ImmutableList.concat(entity.getInputPorts(), entity.getOutputPorts()).forEach(port -> {

            emitter().emit("// -- host buffer for %s", port.getName());

            String scPortType = getSimulationPortType(port);

            emitter().emit("std::vector<%s> buffer_%s;", scPortType, port.getName());

        });


        emitter().emit("// -- options");
        emitter().emit("int vcd_trace_level;");
        emitter().emit("char *profile_file_name;");
        emitter().emit("bool enable_profiling;");
        emitter().emitNewLine();

    }

    default String getSimulationPortType(PortDecl port) {

        Type portType = backend().types().declaredPortType(port);

        String scPortType = "";
        if (portType instanceof IntType) {
            IntType intPortType = (IntType) portType;
            if (intPortType.getSize().isPresent()) {
                int originalSize = intPortType.getSize().getAsInt();
                if (originalSize <= 32) {
                    scPortType = "uint32_t";
                } else if (originalSize <= 64) {
                    scPortType = "uint64_t";
                } else {
                    scPortType = "sc_bv<" + (originalSize * 8) + ">";
                }
            } else {
                scPortType = "uint32_t";
            }
        } else if (portType instanceof BoolType) {
            return scPortType = "bool";
        } else {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Unsupported port type for SystemC simulation"));
        }
        return  scPortType;
    }

    default void deviceState(String name, Entity entity) {

        // -- device object
        emitter().emit("// -- device handle object");
        emitter().emit("std::unique_ptr<ocl_device::DeviceHandle> dev;");

        // -- input ports
        emitter().emit("std::vector<ocl_device::PLinkPort> input_ports;");

        // -- output ports
        emitter().emit("std::vector<ocl_device::PLinkPort> output_ports;");



    }

    default void instanceState(String name, Entity entity) {

        emitter().emit("// -- Instance state structure");
        emitter().emit("typedef struct{");
        {
            emitter().increaseIndentation();
            emitter().emit("AbstractActorInstance base;");
            emitter().emit("int32_t program_counter;");

            if (isSimulated())
                simulationState(name, entity);
            else
                deviceState(name, entity);

            // -- utility vars
            emitter().emitNewLine();
            emitter().emit("uint64_t total_consumed;");
            emitter().emit("uint64_t total_produced;");
            emitter().emit("uint64_t total_request;");
            emitter().emit("bool deadlock_notify;");

            emitter().emit("uint64_t trip_count;");
            // -- guard variable
            emitter().emit("bool should_retry;");

            emitter().decreaseIndentation();
        }
        emitter().emit("} ActorInstance_%s;", name);
        emitter().emitNewLine();
    }

    default void prototypes(String name, Entity entity) {

        String instanceQID = name;
        emitter().emitNewLine();
        emitter().emit("// -- scheduler prototype");
        emitter().emit("ART_ACTION_SCHEDULER(ActorInstance_%s_scheduler);", instanceQID);
        emitter().emitNewLine();
        emitter().emit("// -- constructor and destructor prototype");
        emitter().emit("static void ActorInstance_%s_constructor(AbstractActorInstance *);", instanceQID);
        emitter().emit("static void ActorInstance_%s_destructor(AbstractActorInstance *);", instanceQID);
        emitter().emitNewLine();
    }

    default void portDescription(String name, Entity entity) {
        backend().instance().portDescription(name, entity);
    }

    default void stateVariableDescription(String name, Entity entity) {
        emitter().emit("// -- state variable description");
        emitter().emit("static const StateVariableDescription stateVariableDescriptions[] = { };");
    }

    default void transitionDescription(String name, Entity entity) {
        emitter().emit("// -- Transitions Description");
        emitter().emit("static const ActionDescription actionDescriptions[] = { };");
    }

    default void conditionDescription(String name, Entity entity) {
        emitter().emit("// -- Condition description");
        emitter().emit("static const ConditionDescription conditionDescriptions[] = { };");
    }

    default void actorClass(String name, Entity entity) {

        String instanceQID = name;
        emitter().emit("// -- Actor Class");
        emitter().emit("#ifdef CAL_RT_CALVIN");
        emitter().emit("ActorClass klass");
        emitter().emit("#else");
        emitter().emit("ActorClass ActorClass_%s", instanceQID);
        emitter().emit("#endif");
        {
            emitter().increaseIndentation();
            emitter().emit(" = INIT_ActorClass(");
            {
                emitter().increaseIndentation();
                emitter().emit("\"%s\",", instanceQID);
                emitter().emit("ActorInstance_%s,", instanceQID);
                emitter().emit("ActorInstance_%s_constructor,", instanceQID);
                if (isSimulated())
                    emitter().emit("setParam, ");
                else
                    emitter().emit("0, // -- setParam not needed anymore (we instantiate with params)");
                emitter().emit("ActorInstance_%s_scheduler,", instanceQID);
                emitter().emit("ActorInstance_%s_destructor,", instanceQID);
                emitter().emit("%d, %s,", entity.getInputPorts().size(),
                        entity.getInputPorts().size() == 0 ? "0" : "inputPortDescriptions");
                emitter().emit("%d, %s,", entity.getOutputPorts().size(),
                        entity.getOutputPorts().size() == 0 ? "0" : "outputPortDescriptions");
                emitter().emit("3, actionDescriptions, ");
                emitter().emit("0, conditionDescriptions,");
                emitter().emit("0, stateVariableDescriptions");
                emitter().decreaseIndentation();
            }
            emitter().emit(");");
            emitter().decreaseIndentation();
        }
        emitter().emitNewLine();
    }

    default String getMethod(PartitionHandle handle, String name) {
        return backend().devicehandle().methodName(handle, backend().devicehandle().getMethod(handle, name));
    }

    default void constructSimulator(String name, Entity entity) {


        emitter().emit("if (thisActor->profile_file_name == NULL) {");
        {
            emitter().increaseIndentation();
            emitter().emit("thisActor->enable_profiling = false;");
            emitter().decreaseIndentation();
        }
        emitter().emit("} else {");
        {
            emitter().increaseIndentation();
            emitter().emit("thisActor->enable_profiling = true;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
        emitter().emit("// -- clock period ");
        emitter().emit("sc_time period(10.0, SC_NS);");
        emitter().emit("int trace_level = thisActor->vcd_trace_level;");
        emitter().emit("thisActor->dev = " +
                "std::make_unique<ap_rtl::NetworkTester>(\"plink\", period, 4096, trace_level);");

        emitter().emitNewLine();

        emitter().emit("using PortAddress = ap_rtl::NetworkTester::PortAddress;");

        // -- build the ports
        emitter().emit("// -- build the input ports");
        for (PortDecl port : entity.getInputPorts()) {

            emitter().emit("thisActor->input_ports.emplace_back(PortAddress::%s, buffer_size_%1$s);",
                    port.getName());
        }
        // -- build output ports
        emitter().emit("// -- build the output ports");
        for (PortDecl port : entity.getOutputPorts()) {
            emitter().emit("thisActor->output_ports.emplace_back(PortAddress::%s, buffer_size_%1$s);",
                    port.getName());

        }

        emitter().emitNewLine();
        // -- allocate host buffers
        emitter().emit("// --allocate host buffers");
        ImmutableList.concat(entity.getInputPorts(), entity.getOutputPorts()).forEach(port -> {
            String portType = getSimulationPortType(port);
            emitter().emit("thisActor->buffer_%s.resize(buffer_size_%1$s);",
                    port.getName());
        });

        emitter().emitNewLine();

        // -- allocate device buffers
        emitter().emit("// -- allocate simulation buffers");

        emitter().emitNewLine();
        ImmutableList.concat(entity.getInputPorts(), entity.getOutputPorts()).forEach(port -> {
            emitter().emit("thisActor->dev->allocateMemory(PortAddress::%s, buffer_size_%1$s);", port.getName());
        });
        emitter().emitNewLine();
        // -- reset the device
        emitter().emit("// -- reset the device");
        emitter().emit("thisActor->dev->reset();");

    }

    default void constructDevice(String name, Entity entity) {

        PartitionHandle handle = ((PartitionLink) entity).getHandle();

        ImmutableList<Memories.InstanceVarDeclPair> mems = backend().externalMemory().getExternalMemories();

        ImmutableList<Integer> inputBufferSize = ImmutableList.<Integer>from(
                entity.getInputPorts().stream().map(inputPort ->
                        backend().channelsutils().targetEndSize(new Connection.End(Optional.of(name),
                                inputPort.getName()))).collect(Collectors.toList()));

        ImmutableList<Integer> outputBufferSize = ImmutableList.<Integer>from(
                entity.getOutputPorts().stream().map(outputPort ->
                        backend().channelsutils().sourceEndSize(new Connection.End(Optional.of(name),
                                outputPort.getName()))).collect(Collectors.toList()));

        // -- build the ports

        emitter().emit("// -- get the buffers size");



        emitter().emit("// -- build the input ports");
        for (PortDecl port : entity.getInputPorts()) {
            int index = entity.getInputPorts().indexOf(port);
            int size = inputBufferSize.get(index);
            String type = typeseval().type(types().declaredPortType(port));
            emitter().emit("thisActor->input_ports.emplace_back(\"%s\", buffer_size_%1$s * sizeof(%s));",
                    port.getName(), type);
        }
        // -- build output ports
        emitter().emit("// -- build the output ports");
        for (PortDecl port : entity.getOutputPorts()) {
            int index = entity.getOutputPorts().indexOf(port);
            int size = outputBufferSize.get(index);
            String type = typeseval().type(types().declaredPortType(port));
            emitter().emit("thisActor->output_ports.emplace_back(\"%s\", buffer_size_%1$s * sizeof(%s));",
                    port.getName(), type);

        }

        // -- allocate external memory if any
        {
            ImmutableList<String> memSize = mems.stream().map(memPair -> {
                Long sizeBytes =
                        backend().externalMemory().memories().sizeInBytes(
                                backend().types().declaredType(memPair.getDecl())).orElseThrow(
                                () -> new CompilationException(
                                        new Diagnostic(Diagnostic.Kind.ERROR, "Could not compute " +
                                                "the size of " + memPair.getDecl().getName())));
                return sizeBytes.toString();
            }).collect(ImmutableList.collector());

            // -- build the device object
            emitter().emit("// -- the device object");
            emitter().emit("thisActor->dev = std::make_unique<ocl_device::DeviceHandle>(");
            {
                emitter().increaseIndentation();
                String kernelID = backend().task().getIdentifier().getLast().toString() + "_kernel";
                emitter().emit("%d,  // -- number of inputs", entity.getInputPorts().size());
                emitter().emit("%d,  // -- number of outputs", entity.getOutputPorts().size());
                emitter().emit("%d,  // -- number of external mems", mems.size());
                emitter().emit("\"%s\",  // -- kernel name", kernelID);
                emitter().emit("\"%s\"  // -- kernel dir", "xclbin");
                emitter().decreaseIndentation();
            }
            emitter().emit(");");

            // -- connect the ports
            emitter().emit("// -- connect and allocate ports");

            emitter().emit("thisActor->dev->buildPorts(thisActor->input_ports, thisActor->output_ports);");

            if (memSize.size() > 0) {
                emitter().emit("// --external memories");
                emitter().emit("{");
                {
                    emitter().increaseIndentation();

                    emitter().emit("std::vector<cl::size_type> external_memory_size = {%s};",
                            String.join(",", memSize));
                    emitter().emit("thisActor->dev->allocateExternals(external_memory_size);");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            }
        }

        emitter().emitNewLine();
        emitter().emit("thisActor->total_consumed = 0;");
        emitter().emit("thisActor->total_produced = 0;");
        emitter().emit("thisActor->total_request = 0;");
        emitter().emit("thisActor->should_retry = false;");
        emitter().emitNewLine();

    }

    default void constructorDefinition(String name, Entity entity) {

        emitter().emit("// -- Constructor definition");

        String actorInstanceName = "ActorInstance_" + name;


        emitter().emit("static void %s_constructor(AbstractActorInstance *pBase) {", actorInstanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("%s *thisActor = (%1$s *) pBase;", actorInstanceName);

            emitter().emit("thisActor->program_counter = 0;");
            emitter().emit("thisActor->deadlock_notify = true;");
            emitter().emit("thisActor->trip_count = 0;");

            emitter().emit("// -- buffer size info");
            for(PortDecl port: entity.getInputPorts()) {
                int ix = entity.getInputPorts().indexOf(port);
                emitter().emit("const std::size_t buffer_size_%s = thisActor->base.input[%d].capacity;",
                        port.getName(), ix);
            }
            for(PortDecl port: entity.getOutputPorts()) {
                int ix = entity.getOutputPorts().indexOf(port);

                emitter().emit("const std::size_t buffer_size_%s = thisActor->base.output[%d].capacity;",
                        port.getName(), ix);
            }

            if (isSimulated())
                constructSimulator(name, entity);
            else
                constructDevice(name, entity);

            emitter().emitNewLine();

            emitter().emit("#ifdef CAL_RT_CALVIN");
            emitter().emit("init_global_variables();");
            emitter().emit("#endif");
            emitter().emitNewLine();
            emitter().decreaseIndentation();

        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void destructSimulator(String name, Entity entity) {
        // automatic destruction using smart pointers
        emitter().emit("STATUS_REPORT(\"PLink trip count = %%lu\\n\", thisActor->trip_count);");
    }

    default void destructDevice(String name, Entity entity) {
        PartitionHandle handle = ((PartitionLink) entity).getHandle();
        // -- device destructor
        emitter().emit("printf(\"PLink trip count = %%lu\\n\", thisActor->trip_count);");
    }

    default void destructorDefinition(String name, Entity entity) {
        emitter().emit("// -- destructor definition");
        String actorInstanceName = "ActorInstance_" + name;


        emitter().emit("static void %s_destructor(AbstractActorInstance *pBase) {", actorInstanceName);
        {
            emitter().increaseIndentation();

            emitter().emit("%s *thisActor = (%1$s *) pBase;", actorInstanceName);
            emitter().emitNewLine();


            // -- device destructor
            if (isSimulated())
                destructSimulator(name, entity);
            else
                destructDevice(name, entity);

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }


    default void deviceScheduler(String name, Entity entity) {

        PartitionHandle handle = ((PartitionLink) entity).getHandle();
        emitter().emit("ART_ACTION_SCHEDULER_ENTER(%d, %d);", entity.getInputPorts().size(),
                entity.getOutputPorts().size());
        emitter().emitNewLine();

        emitter().emit("switch (thisActor->program_counter) {");
        {
            emitter().increaseIndentation();
            emitter().emit("case 0: goto S0;");
            emitter().emit("case 3: goto S3;");
            emitter().emit("case 4: goto S4;");
            emitter().emit("case 5: goto S5;");
            emitter().emit( "default: runtimeError(pBase, \"invalid plink state\");");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().emit("S0 : // Init state\n" +
                "{\n" +
                "  thisActor->deadlock_notify = true;\n" +
                "  if (ART_TEST_CONDITION(input_condition))\n" +
                "    goto S1;\n" +
                "  else\n" +
                "    goto S8;\n" +
                "}\n" +
                "S1 : // has input, must check output space\n" +
                "{\n" +
                "  if (ART_TEST_CONDITION(output_condition))\n" +
                "    goto S2;\n" +
                "  else\n" +
                "    goto S7;\n" +
                "}\n" +
                "S2 : // has input, has output space, must transmit\n" +
                "{\n" +
                "  thisActor->program_counter = 3;\n" +
                "  ART_EXEC_TRANSITION(transmit);\n" +
                "  goto YIELD;\n" +
                "}\n" +
                "S3 : // Transmitted, must receive\n" +
                "{\n" +
                "  thisActor->program_counter = 4;\n" +
                "  ART_EXEC_TRANSITION(receive);\n" +
                "  goto YIELD;\n" +
                "}\n" +
                "S4 : // Received, must write\n" +
                "{\n" +
                "  thisActor->program_counter = 5;\n" +
                "  ART_EXEC_TRANSITION(write);\n" +
                "}\n" +
                "S5 : // Wrote, check for deadlock?\n" +
                "{\n" +
                "  if (ART_TEST_CONDITION(deadlock_check))\n" +
                "    goto S6;\n" +
                "  else {\n" +
                "    thisActor->program_counter = 0;\n" +
                "    goto YIELD;\n" +
                "  }\n" +
                "}\n" +
                "S6 : // Terminal error, deadlock\n" +
                "{\n" +
                "  runtimeError(pBase, \"Potential device deadlock!\\n\");\n" +
                "}\n" +
                "S7 : // Has input, no output space, must transmit\n" +
                "{\n" +
                "  thisActor->program_counter = 3;\n" +
                "  ART_EXEC_TRANSITION(transmit);\n" +
                "  goto YIELD;\n" +
                "}\n" +
                "S8 : // no input, check output space\n" +
                "{\n" +
                "  if (ART_TEST_CONDITION(output_condition))\n" +
                "    goto S9;\n" +
                "  else {\n" +
                "    thisActor->program_counter = 0;\n" +
                "    goto YIELD;\n" +
                "  }\n" +
                "}\n" +
                "S9 : // has output space, no input though, check retry guard\n" +
                "{\n" +
                "  if (ART_TEST_CONDITION(retry_guard))\n" +
                "    goto S10;\n" +
                "  else {\n" +
                "    thisActor->program_counter = 0;\n" +
                "    goto YIELD;\n" +
                "  }\n" +
                "}\n" +
                "S10 : // retry true, no input, but has output spac, should transmit\n" +
                "{\n" +
                "  %s(\"Retrying kernel with no inputs\\n\")\n" +
                "  thisActor->program_counter = 3;\n" +
                "  ART_EXEC_TRANSITION(transmit);\n" +
                "  goto YIELD;\n" +
                "}", isSimulated() ? "STATUS_REPORT" : "OCL_MSG");

        emitter().emit("YIELD: {");
        {
            emitter().increaseIndentation();
            emitter().emit("ART_ACTION_SCHEDULER_EXIT(%d, %d)", entity.getInputPorts().size(),
                    entity.getOutputPorts().size());
            emitter().emit("return result;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }
    default void scheduler(String name, Entity entity) {


        emitter().emit("// -- scheduler definitions");
        emitter().emit("static const int exitcode_block_any[3] = {1, 0, 1};");
        emitter().emitNewLine();
        String actorInstanceName = "ActorInstance_" + name;
        emitter().emit("ART_ACTION_SCHEDULER(%s_scheduler){", actorInstanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("const int *result = exitcode_block_any;");
            emitter().emit("static const int exitCode[] = {EXITCODE_BLOCK(1), 0 , 1};");
            emitter().emitNewLine();
            emitter().emit("%s *thisActor = (%1$s *) pBase;", actorInstanceName);
            emitter().emitNewLine();


            deviceScheduler(name, entity);

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }



    default void getSetParam(String name, Entity entity) {

        emitter().emit("static void setParam(AbstractActorInstance *pBase, const char *paramName, const char *value) {");
        {
            emitter().increaseIndentation();
            emitter().emit("ActorInstance_%s *thisActor = (ActorInstance_%1$s *) pBase;", name);
            emitter().emit("if (strcmp(paramName, \"vcd-trace-level\") == 0) {");
            {
                emitter().increaseIndentation();
                emitter().emit("thisActor->vcd_trace_level = atoi(value);");
                emitter().decreaseIndentation();
            }
            emitter().emit("} else if (strcmp(paramName, \"profile-file-name\") == 0) { ");
            {
                emitter().increaseIndentation();
                emitter().emit("thisActor->profile_file_name = strdup(value);");
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("runtimeError(pBase, \"No such parameter: %%s\", paramName);");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            emitter().decreaseIndentation();

        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void actionDefinitions(String instanceName, Entity entity) {

        if(isSimulated()) {

            getTransmitActionSim(instanceName, entity);
            emitter().emitNewLine();


            getReceiveActionSim(instanceName, entity);
            emitter().emitNewLine();


            getWriteActionSim(instanceName, entity);
            emitter().emitNewLine();

        } else {

            getTransmitAction(instanceName, entity);
            emitter().emitNewLine();


            getReceiveAction(instanceName, entity);
            emitter().emitNewLine();


            getWriteAction(instanceName, entity);
            emitter().emitNewLine();
        }


    }

    default void conditionDefinitions(String instanceName, Entity entity) {

        if (isSimulated()) {
            getInputConditionSim(instanceName, entity);
            emitter().emitNewLine();

            getOutputConditionSim(instanceName, entity);
            emitter().emitNewLine();

            getRetryConditionSim(instanceName, entity);
            emitter().emitNewLine();

            getDeadlockConditionSim(instanceName, entity);
            emitter().emitNewLine();

        } else {
            getInputCondition(instanceName, entity);
            emitter().emitNewLine();

            getOutputCondition(instanceName, entity);
            emitter().emitNewLine();

            getRetryCondition(instanceName, entity);
            emitter().emitNewLine();

            getDeadlockCondition(instanceName, entity);
            emitter().emitNewLine();
        }


    }

    default void getInputCondition(String instanceName, Entity entity) {

        emitter().emit("ART_CONDITION(input_condition, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("uint32_t avail_in = 0;");
            emitter().emit("bool cond = false;");
            for(PortDecl input: entity.getInputPorts()) {
                String type = typeseval().type(types().declaredPortType(input));
                int ix = entity.getInputPorts().indexOf(input);
                emitter().emit("avail_in = pinAvailIn_%s(IN%d_%s);", type, ix, input.getName());
                emitter().emit("if (avail_in != thisActor->input_ports[%d].usable)", ix);
                emitter().emit("\tthisActor->deadlock_notify = false;");
                emitter().emit("if (avail_in > 0)");
                emitter().emit("\tcond=true;");
                emitter().emit("thisActor->input_ports[%d].usable = avail_in;", ix);
            }
            emitter().emitNewLine();
            emitter().emitNewLine();
            emitter().emit("return cond;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getInputConditionSim(String instanceName, Entity entity) {

        getInputCondition(instanceName, entity);
    }

    default void getOutputCondition(String instanceName, Entity entity) {
        emitter().emit("ART_CONDITION(output_condition, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("bool cond = false;");
            emitter().emit("uint32_t avail_out = 0;");
            for(PortDecl output: entity.getOutputPorts()) {
                String type = typeseval().type(types().declaredPortType(output));
                int ix = entity.getOutputPorts().indexOf(output);
                emitter().emit("avail_out = pinAvailOut_%s(OUT%d_%s);", type, ix, output.getName());
                emitter().emit("if(avail_out != thisActor->output_ports[%d].usable || avail_out == 0)", ix);
                emitter().emit("\tthisActor->deadlock_notify = false;");
                emitter().emit("if(avail_out > 0)");
                emitter().emit("\tcond = true;");
                emitter().emit("thisActor->output_ports[%d].usable = avail_out;", ix);
            }
            emitter().emitNewLine();
            emitter().emit("return cond;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getOutputConditionSim(String instanceName, Entity entity) {
        getOutputCondition(instanceName, entity);
    }

    default void getRetryCondition(String instanceName, Entity entity) {
        emitter().emit("ART_CONDITION(retry_guard, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();

            emitter().emit("return thisActor->should_retry;");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getRetryConditionSim(String instanceName, Entity entity) {
        getRetryCondition(instanceName, entity);
    }

    default void getDeadlockCondition(String instanceName, Entity entity) {
        emitter().emit("ART_CONDITION(deadlock_check, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();

            emitter().emit(
                    "\tbool has_consumption = false;\n" +
                    "\tfor (auto& input : thisActor->input_ports) {\n" +
                    "\t\tif (input.used > 0)\n" +
                    "\t\t\thas_consumption = true;\n" +
                    "\t}\n" +
                    "\tbool has_production = false;\n" +
                    "\tfor (auto & output: thisActor->output_ports) {\n" +
                    "\t\tif (output.used > 0)\n" +
                    "\t\t\thas_production = true;\n" +
                    "\t}");
            emitter().emit("bool cond =  thisActor->deadlock_notify && (!has_consumption) && (!has_production);");
            emitter().emit("return cond;");

            emitter().decreaseIndentation();

        }
        emitter().emit("}");
    }

    default void getDeadlockConditionSim(String instanceName, Entity entity) {
        getDeadlockCondition(instanceName, entity);
    }

    default void getTransmitAction(String instanceName, Entity entity) {

        emitter().emit("ART_ACTION(transmit, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();

            emitter().emit("ART_ACTION_ENTER(transmit, 0);");

            emitter().emit("// -- set the request size");
            for(PortDecl input : entity.getInputPorts()) {
                String type = typeseval().type(types().declaredPortType(input));
                emitter().emit("thisActor->dev->setUsableInput<%s>(thisActor->input_ports[%d].port, " +
                        "thisActor->input_ports[%2$d].usable);", type, entity.getInputPorts().indexOf(input));
            }
            emitter().emit("// -- set the available output space for the device");
            for(PortDecl output : entity.getOutputPorts()) {
                String type = typeseval().type(types().declaredPortType(output));
                emitter().emit("thisActor->dev->setUsableOutput<%s>(thisActor->output_ports[%d].port, " +
                        "thisActor->output_ports[%2$d].usable);", type, entity.getOutputPorts().indexOf(output));
            }

            emitter().emitNewLine();
            emitter().emit("// -- copy input FIFOs");
            for(PortDecl input : entity.getInputPorts()) {
                String type = typeseval().type(types().declaredPortType(input));
                emitter().emit("pinPeekRepeat_%s(" +
                        "IN%d_%s, " +
                        "(%1$s *) " +
                        "(thisActor->dev->getInputHostBuffer(thisActor->input_ports[%2$d].port)), " +
                        "thisActor->input_ports[%2$d].usable);",
                        type,
                        entity.getInputPorts().indexOf(input),
                        input.getName());
            }
            emitter().emitNewLine();

            emitter().emit("// -- enqueue the execution of the kernel");
            emitter().emit("thisActor->dev->enqueueWriteBuffers();");
            emitter().emit("thisActor->dev->setArgs();");
            emitter().emit("thisActor->dev->enqueueExecution();");
            emitter().emit("thisActor->dev->enqueueReadSize();");
            emitter().emit("thisActor->trip_count ++;");
            emitter().emitNewLine();

            emitter().emit("ART_ACTION_EXIT(transmit, 0);");

            emitter().decreaseIndentation();

        }
        emitter().emit("}");

    }

    default void getTransmitActionSim(String instanceName, Entity entity) {
        emitter().emit("ART_ACTION(transmit, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();

            emitter().emit("ART_ACTION_ENTER(transmit, 0);");

            emitter().emitNewLine();
            for(PortDecl input : entity.getInputPorts()) {
                int ix = entity.getInputPorts().indexOf(input);
                emitter().emit("thisActor->dev->setArg(thisActor->input_ports[%d].port, " +
                        "thisActor->input_ports[%1$d].usable);", ix);
                emitter().emit("STATUS_REPORT(\"%s request size %%lu\\n\", thisActor->input_ports[%d].usable);",
                        input.getName(), ix);

            }
            emitter().emit("// -- set the available output space for the device");
            for(PortDecl output : entity.getOutputPorts()) {

                int ix = entity.getOutputPorts().indexOf(output);

                emitter().emit("thisActor->dev->setArg(thisActor->output_ports[%d].port, " +
                        "thisActor->output_ports[%1$d].usable);", ix);
                emitter().emit("STATUS_REPORT(\"%s available size %%lu\\n\", thisActor->output_ports[%d].usable);",
                        output.getName(), ix);
            }


            emitter().emitNewLine();

            for(PortDecl input: entity.getInputPorts()) {
                int ix = entity.getInputPorts().indexOf(input);
                getSimulatorPinPeek(input, ix);
                emitter().emit("thisActor->dev->writeDeviceMemory(thisActor->input_ports[%d].port, " +
                        "thisActor->buffer_%s," +
                        "thisActor->input_ports[%1$d].usable);", ix, input.getName());
            }

            emitter().emit("auto sim = thisActor->dev->simulate();");
            emitter().emit("thisActor->trip_count ++;");
            emitter().emit("STATUS_REPORT(\"Simulation call (%%lu) ended after %%llu cycles\\n\", thisActor->trip_count, sim);");

            emitter().emit("ART_ACTION_EXIT(transmit, 0);");
            emitter().emitNewLine();

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }
    default void getReceiveAction(String instanceName, Entity entity) {

        emitter().emit("ART_ACTION(receive, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("ART_ACTION_ENTER(receive, 1);");

            emitter().emit("// -- wait for the size registers");
            emitter().emit("thisActor->dev->waitForSize();");

            emitter().emit("// -- read the device output buffers (async)");
            emitter().emit("thisActor->dev->enqueueReadBuffers();");

            emitter().emit("// -- get the used inputs");
            for (PortDecl input : entity.getInputPorts()) {
                int ix = entity.getInputPorts().indexOf(input);
                String type = typeseval().type(types().declaredPortType(input));
                emitter().emit("thisActor->input_ports[%d].used = ", ix);
                emitter().emit("\tthisActor->dev->getUsedInput<%s>(thisActor->input_ports[%d].port);", type, ix);
                emitter().emit("OCL_MSG(\"Device consumed %%d tokens on port %s\\n\", " +
                        "thisActor->input_ports[%d].used);", input.getName(), ix);
            }

            emitter().emitNewLine();
            emitter().emit("// -- get the produced output");
            for (PortDecl output : entity.getOutputPorts()) {
                int ix = entity.getOutputPorts().indexOf(output);
                String type = typeseval().type(types().declaredPortType(output));
                emitter().emit("thisActor->output_ports[%d].used = ", ix);
                emitter().emit("thisActor->dev->getUsedOutput<%s>(thisActor->output_ports[%d].port);", type, ix);
                emitter().emit("OCL_MSG(\"Device produced %%d tokens on port %s\\n\"" +
                        ", thisActor->output_ports[%d].used);", output.getName(), ix);
            }

            // -- consume
            emitter().emit("// -- consume on behalf of device");
            for (PortDecl input : entity.getInputPorts()) {
                int ix = entity.getInputPorts().indexOf(input);
                String type = typeseval().type(types().declaredPortType(input));
                emitter().emit("if (thisActor->input_ports[%d].used > 0) {", ix);
                {
                    emitter().increaseIndentation();

                    emitter().emit("pinConsumeRepeat_%s(IN%d_%s,", type, ix, input.getName());
                    emitter().emit("\tthisActor->input_ports[%d].used);", ix);

                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            }

            emitter().emitNewLine();

            emitter().emit("thisActor->total_request = 0;");
            emitter().emit("thisActor->total_consumed = 0;");
            emitter().emit("thisActor->total_produced = 0;");
            emitter().emit("thisActor->should_retry = false;");
            emitter().emitNewLine();

            emitter().emit("for(auto& output : thisActor->output_ports) {");
            {
                emitter().increaseIndentation();
                emitter().emit("if(output.used == output.usable) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("thisActor->should_retry = true;");
                    emitter().emit("OCL_MSG(\"Retry condition set to true\\n\");");
                    emitter().emit("break;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emit("ART_ACTION_ENTER(receive, 1);");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getReceiveActionSim(String instanceName, Entity entity) {

        emitter().emit("ART_ACTION(receive, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("ART_ACTION_ENTER(receive, 1);");

            emitter().emit("// -- get the used inputs");
            for (PortDecl input : entity.getInputPorts()) {
                int ix = entity.getInputPorts().indexOf(input);

                emitter().emit("thisActor->input_ports[%d].used = ", ix);
                emitter().emit("\tthisActor->dev->querySize(thisActor->input_ports[%d].port);", ix);
                emitter().emit("STATUS_REPORT(\"Device consumed %%d tokens on port %s\\n\", " +
                        "thisActor->input_ports[%d].used);", input.getName(), ix);
            }

            emitter().emitNewLine();
            emitter().emit("// -- get the produced output");
            for (PortDecl output : entity.getOutputPorts()) {
                int ix = entity.getOutputPorts().indexOf(output);

                emitter().emit("thisActor->output_ports[%d].used = ", ix);
                emitter().emit("\tthisActor->dev->querySize(thisActor->output_ports[%d].port);", ix);
                emitter().emit("STATUS_REPORT(\"Device produced %%d tokens on port %s\\n\"" +
                        ", thisActor->output_ports[%d].used);", output.getName(), ix);
            }

            // -- consume
            emitter().emit("// -- consume on behalf of device");
            for (PortDecl input : entity.getInputPorts()) {
                int ix = entity.getInputPorts().indexOf(input);
                String type = typeseval().type(types().declaredPortType(input));
                emitter().emit("if (thisActor->input_ports[%d].used > 0) {", ix);
                {
                    emitter().increaseIndentation();

                    emitter().emit("pinConsumeRepeat_%s(IN%d_%s,", type, ix, input.getName());
                    emitter().emit("\tthisActor->input_ports[%d].used);", ix);

                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            }

            emitter().emitNewLine();

            emitter().emit("thisActor->total_request = 0;");
            emitter().emit("thisActor->total_consumed = 0;");
            emitter().emit("thisActor->total_produced = 0;");
            emitter().emit("thisActor->should_retry = false;");
            emitter().emitNewLine();

            emitter().emit("for(auto& output : thisActor->output_ports) {");
            {
                emitter().increaseIndentation();
                emitter().emit("if(output.used == output.usable) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("thisActor->should_retry = true;");
                    emitter().emit("STATUS_REPORT(\"Retry condition set to true\\n\");");
                    emitter().emit("break;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().decreaseIndentation();
            }
            // -- dump systemc profiling info
            emitter().emit("// -- dump systemc profiling info");
            emitter().emit("if (thisActor->enable_profiling == true) {");
            {
                emitter().increaseIndentation();
                emitter().emit("std::ofstream ofs(thisActor->profile_file_name, std::ios::out);");
                emitter().emit("if (ofs.is_open()) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("thisActor->dev->dumpStats(ofs);");
                    emitter().emit("ofs.close();");
                    emitter().decreaseIndentation();
                }
                emitter().emit("} else { ");
                {
                    emitter().increaseIndentation();
                    emitter().emit("PANIC(\"Could not open %%s to report SystemC " +
                            "profiling info.\\n\", thisActor->profile_file_name);");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().emitNewLine();

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();
            emitter().emit("}");
            emitter().emit("ART_ACTION_ENTER(receive, 1);");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }
    default void getWriteAction(String instanceName, Entity entity) {

        emitter().emit("ART_ACTION(write, ActorInstance_%s) { ", instanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("ART_ACTION_ENTER(write, 2);");

            emitter().emit("thisActor->dev->waitForReadBuffers();");

            emitter().emit("{");
            {
                emitter().increaseIndentation();

                emitter().emit("std::array<uint32_t, %d> avail_space;", entity.getOutputPorts().size());
                for(PortDecl output : entity.getOutputPorts()) {
                    String type = typeseval().type(types().declaredPortType(output));
                    int ix = entity.getOutputPorts().indexOf(output);
                    emitter().emit("avail_space[%d] = pinAvailOut_%s(OUT%1$d_%s);",
                            ix, type, output.getName());
                    emitter().emit("OCL_ASSERT(avail_space[%d] >= thisActor->output_ports[%1$d].used, \"Output %1$d overflow!\\n\");", ix);
                }



                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            for(PortDecl output : entity.getOutputPorts()) {
                emitter().emit("{");
                {
                    emitter().increaseIndentation();

                    String type = typeseval().type(types().declaredPortType(output));
                    int ix = entity.getOutputPorts().indexOf(output);

                    emitter().emit("%s* buf =  " +
                            "(%1$s*) thisActor->dev->getOutputHostBuffer(thisActor->output_ports[%d].port);", type, ix);
                    emitter().emit("pinWriteRepeat_%s(OUT%d_%s, buf, thisActor->output_ports[%2$d].used);",
                            type, ix, output.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            }
            emitter().emit("thisActor->dev->releaseEvents();");
            emitter().emit("ART_ACTION_EXIT(write, 2);");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }


    default void getWriteActionSim(String instanceName, Entity entity) {

        emitter().emit("ART_ACTION(write, ActorInstance_%s) { ", instanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("ART_ACTION_ENTER(write, 2);");



            emitter().emit("{");
            {
                emitter().increaseIndentation();

                emitter().emit("std::array<uint32_t, %d> avail_space;", entity.getOutputPorts().size());
                for(PortDecl output : entity.getOutputPorts()) {
                    String type = typeseval().type(types().declaredPortType(output));
                    int ix = entity.getOutputPorts().indexOf(output);
                    emitter().emit("avail_space[%d] = pinAvailOut_%s(OUT%1$d_%s);",
                            ix, type, output.getName());
                    emitter().emit("ASSERT(avail_space[%d] >= thisActor->output_ports[%1$d].used, \"Output %1$d overflow!\\n\");", ix);
                }



                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            for(PortDecl output : entity.getOutputPorts()) {

                int ix = entity.getOutputPorts().indexOf(output);
                emitter().emit("thisActor->dev->readDeviceMemory(thisActor->output_ports[%d].port,", ix);
                emitter().emit("\tthisActor->buffer_%s, ", output.getName());
                emitter().emit("\tthisActor->output_ports[%d].used);", ix);
                getSimulatorPinWrite(output, ix);


            }

            emitter().emit("ART_ACTION_EXIT(write, 2);");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

    }


    default void getSimulatorPinPeek(PortDecl port, int index) {
        emitter().emit("for (std::size_t i = 0; i < thisActor->input_ports[%d].usable; i++)", index);
        {
            emitter().increaseIndentation();
            String type = backend().typesEval().type(backend().types().declaredPortType(port));
            emitter().emit("thisActor->buffer_%s[i] = pinPeek_%s(IN%d_%1$s, i);", port.getName(), type, index);
            emitter().decreaseIndentation();
        }

        emitter().emitNewLine();
    }

    default void getSimulatorPinWrite(PortDecl port, int index) {

        emitter().emit("for (std::size_t i = 0; i < thisActor->output_ports[%d].used; i++)", index);
        {
            emitter().increaseIndentation();
            String type = backend().typesEval().type(backend().types().declaredPortType(port));
            emitter().emit("pinWrite_%s(OUT%d_%s, thisActor->buffer_%3$s[i]);", type, index, port.getName());
            emitter().decreaseIndentation();
        }
        emitter().emitNewLine();

    }


}
