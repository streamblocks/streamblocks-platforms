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
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.TemplateAnalysisPhase$Analysis$MultiJ;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;
import sun.reflect.generics.reflectiveObjects.GenericArrayTypeImpl;


import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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

        ImmutableList.concat(entity.getInputPorts(), entity.getOutputPorts()).forEach(port -> {

            emitter().emit("// -- host buffer for %s", port.getName());

            String scPortType = getSimulationPortType(port);

            emitter().emit("std::vector<%s> buffer_%s;", scPortType, port.getName());

        });

        entity.getOutputPorts().forEach(port -> {

            emitter().emit("// -- output host buffer offset pointers");
            emitter().emit("std::size_t buffer_offset_%s;", port.getName());
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

        // -- utility vars
        emitter().emitNewLine();
        emitter().emit("uint64_t total_consumed;");
        emitter().emit("uint64_t total_produced;");
        emitter().emit("uint64_t total_request;");

        // -- guard variable
        emitter().emit("bool should_retry;");
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
                "std::make_unique<ap_rtl::NetworkTester>(\"plink\", period, 512, trace_level);");

        emitter().emitNewLine();
        for(PortDecl port: entity.getInputPorts()) {
            int bufferSize = backend().channelsutils().targetEndSize(
                    new Connection.End(Optional.of(name),port.getName()));
            emitter().emit("const std::size_t buffer_size_%s = %d;", port.getName(),bufferSize);
        }
        for(PortDecl port: entity.getOutputPorts()) {
            int bufferSize = backend().channelsutils().sourceEndSize(
                    new Connection.End(Optional.of(name),port.getName()));
            emitter().emit("const std::size_t buffer_size_%s = %d;", port.getName(), bufferSize);
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
        emitter().emit("// -- reset the output buffer offset pointers");
        for (PortDecl outputPort : entity.getOutputPorts()) {
            emitter().emit("thisActor->buffer_offset_%s = 0;", outputPort.getName());
        }
        // -- allocate device buffers
        emitter().emit("// -- allocate simulation buffers");
        emitter().emit("using PortAddress = ap_rtl::NetworkTester::PortAddress;");
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
        emitter().emit("// -- build the input ports");
        for (PortDecl port : entity.getInputPorts()) {
            int index = entity.getInputPorts().indexOf(port);
            int size = inputBufferSize.get(index);
            String type = typeseval().type(types().declaredPortType(port));
            emitter().emit("thisActor->input_ports.emplace_back(\"%s\", %d * sizeof(%s));",
                    port.getName(), size, type);
        }
        // -- build output ports
        emitter().emit("// -- build the output ports");
        for (PortDecl port : entity.getOutputPorts()) {
            int index = entity.getOutputPorts().indexOf(port);
            int size = outputBufferSize.get(index);
            String type = typeseval().type(types().declaredPortType(port));
            emitter().emit("thisActor->output_ports.emplace_back(\"%s\", %d * sizeof(%s));",
                    port.getName(), size, type);

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

            if (memSize.size() > 0) {
                emitter().emit("// --external memories");
                emitter().emit("{");
                {
                    emitter().increaseIndentation();

                    emitter().emit("std::vector<uint32_t> external_memory_size = {%s};",
                            String.join(",", memSize));
                    emitter().emit("thisActor->dev->allocateExternals(external_memory_size);");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            }

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
                emitter().emit("\"%s\",  // -- kernel dir", "xclbin");
                emitter().emit("\"%s\"  // -- device name", "xilinx_kcu1500_dynamic_5_0");

                emitter().decreaseIndentation();
            }
            emitter().emit(");");

            // -- connect the ports
            emitter().emit("// -- connect and allocate ports");

            emitter().emit("thisActor->dev->buildPorts(thisActor->input_ports, thisActor->output_ports);");
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
    }

    default void destructDevice(String name, Entity entity) {
        PartitionHandle handle = ((PartitionLink) entity).getHandle();
        // -- device destructor

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


    default void simulationScheduler(String name, Entity entity) {


        emitter().emitNewLine();
        emitter().emit("ART_ACTION_SCHEDULER_ENTER(%d, %d)", entity.getInputPorts().size(),
                entity.getOutputPorts().size());
        emitter().emitNewLine();
        emitter().emit("switch (thisActor->program_counter) {");
        {
            emitter().increaseIndentation();

            emitter().emit("case 0: goto CHECK;");
            emitter().emit("case 1: goto SIMULATE;");
            emitter().emit("case 2: goto WRITE;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();

        emitter().emit("CHECK: {");
        {
            emitter().increaseIndentation();
            // -- count the tokens
            for (PortDecl port : entity.getInputPorts()) {
                String type = typeseval().type(types().declaredPortType(port));
                int portId = entity.getInputPorts().indexOf(port);
                emitter().emit("uint32_t tokens_count_%s = pinAvailIn_%s(IN%d_%1$s);", port.getName(), type,
                        portId);
                emitter().emit("if (tokens_count_%s > 0) {", port.getName());
                {
                    emitter().increaseIndentation();
                    getSimulatorPinPeek(port, entity.getInputPorts().indexOf(port));

                    emitter().decreaseIndentation();
                }
                emitter().emit("}");

            }
            emitter().emitNewLine();

            emitter().emit("thisActor->program_counter = 1;");
            emitter().emit("goto SIMULATE;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().emitNewLine();
        emitter().emit("SIMULATE: {");
        {
            emitter().increaseIndentation();
            emitter().emit("using PortAddress = ap_rtl::NetworkTester::PortAddress;");
            emitter().emit("std::size_t total_req_sz = 0;");
            // -- write host buffers to device
            emitter().emit("// -- write host buffers to device and set the arguments");
            for (PortDecl inputPort : entity.getInputPorts()) {
                String type = typeseval().type(types().declaredPortType(inputPort));
                emitter().emit("std::size_t req_sz_%s = pinAvailIn_%s(IN%d_%1$s);",
                        inputPort.getName(), type, entity.getInputPorts().indexOf(inputPort));
                emitter().emit("thisActor->dev->writeDeviceMemory(PortAddress::%s, thisActor->buffer_%1$s, req_sz_%1$s);",
                        inputPort.getName());
                emitter().emit("thisActor->dev->setArg(PortAddress::%s, req_sz_%1$s);", inputPort.getName());
                emitter().emit("total_req_sz += req_sz_%s;", inputPort.getName());
            }
            emitter().emitNewLine();

            for (PortDecl outputPort : entity.getOutputPorts()) {

                emitter().emit("thisActor->dev->setArg(PortAddress::%s, thisActor->buffer_%1$s.size());",
                        outputPort.getName());
            }



            // -- simulate
            emitter().emit("// -- simulate");

            emitter().emit("auto sim_ticks = thisActor->dev->simulate();");

            emitter().emitNewLine();

            // -- check production of tokens
            emitter().emit("// -- check production");
            emitter().emit("std::size_t total_produced = 0;");

            // -- read produced tokens back
            emitter().emit("// -- read produced tokens");
            for (PortDecl outputPort : entity.getOutputPorts()) {
                emitter().emit("thisActor->dev->readDeviceMemory(PortAddress::%s, thisActor->buffer_%1$s, thisActor->dev->querySize(PortAddress::%1$s));",
                        outputPort.getName());
                emitter().emit("total_produced += thisActor->dev->querySize(PortAddress::%s);",
                        outputPort.getName());
                emitter().emit("thisActor->buffer_offset_%s = 0;", outputPort.getName());
            }

            emitter().emitNewLine();
            // -- consume
            emitter().emit("// -- consume");
            emitter().emit("std::size_t total_consumed = 0;");
            for (PortDecl inputPort: entity.getInputPorts()) {

                emitter().emit("const std::size_t consumed_%s = thisActor->dev->querySize(PortAddress::%1$s);",
                        inputPort.getName());
                String type = typeseval().type(types().declaredPortType(inputPort));
                emitter().emit("pinConsumeRepeat_%s(IN%d_%s, consumed_%3$s);", type,
                        entity.getInputPorts().indexOf(inputPort), inputPort.getName());
                emitter().emit("total_consumed += consumed_%s;", inputPort.getName());


            }
            emitter().emitNewLine();
            emitter().emit("thisActor->program_counter = 2;");
            emitter().emitNewLine();

            // -- dump systemc profiling info
            emitter().emit("// -- dump systemc profiling info");
            emitter().emit("if (thisActor->enable_profiling == true) {");
            {
                emitter().increaseIndentation();
                emitter().emit("std::ofstream ofs(thisActor->profile_file_name, std::ios::out);", name);
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
                    emitter().emit("runtimeError(pBase, \"Could not open %%s to report SystemC " +
                            "profiling info.\\n\", thisActor->profile_file_name);");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().emitNewLine();

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            // -- notify the multicore scheduler that something useful happened
            emitter().emit("// -- notify the multicore scheduler that something has happened");

            // -- check for potential device deadlock
            getDeadlockCheck("total_produced", "total_consumed", "total_req_sz");

            emitter().emitNewLine();
            // -- notify the scheduler that an action has been performed
            emitter().emit("if (total_consumed > 0 || total_produced > 0) {");
            {
                emitter().increaseIndentation();
                emitter().emit("ART_ACTION_ENTER(CONSUME, 0);");
                emitter().emit("ART_ACTION_EXIT(CONSUME, 0);");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emit("goto WRITE;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().emit("WRITE : {");
        {
            emitter().increaseIndentation();
            emitter().emit("std::size_t tokens_written = 0;");
            emitter().emit("uint32_t done_writing = 0;");
            emitter().emit("using PortAddress = ap_rtl::NetworkTester::PortAddress;");
            // -- try to read from the sim buffers and write to multicore buffers
            for (PortDecl port : entity.getOutputPorts()) {
                emitter().emit("// -- reading sim buffer %s", port.getName());
                emitter().emitNewLine();
                String type = typeseval().type(types().declaredPortType(port));
                int portId = entity.getOutputPorts().indexOf(port);

                emitter().emit("std::size_t offset_%s = thisActor->buffer_offset_%1$s;", port.getName());
                String offset = "thisActor->buffer_offset_" + port.getName();
                String produced = "thisActor->dev->querySize(PortAddress::" + port.getName() + ")";
                String outputPort = String.format("OUT%d_%s", portId, port.getName());
                String remain = "remain_" + port.getName();
                String pinAvailOut = "pinAvailOut_" + type + "(" + outputPort + ")";
                String toWrite = "to_write_" + port.getName();
                emitter().emit("uint32_t %s = %s - %s;", remain, produced, offset);
                emitter().emit("uint32_t %s = MIN(%s, %s);", toWrite, pinAvailOut, remain);


                emitter().emitNewLine();
                // -- there are tokens
                emitter().emit("if (%s > 0) {", remain);
                {
                    emitter().increaseIndentation();
                    emitter().emitNewLine();
                    getSimulatorPinWrite(port, entity.getOutputPorts().indexOf(port));

                    emitter().emit("%s += %s;", offset, toWrite);

                    emitter().emit("tokens_written += %s;", toWrite);
                    emitter().emit("if (%s == %s) {", offset, produced);
                    {
                        emitter().increaseIndentation();
                        emitter().emit("done_writing ++;");
                        emitter().decreaseIndentation();
                    }
                    emitter().emit("}");
                    emitter().emitNewLine();
                    emitter().decreaseIndentation();
                }
                emitter().emit("} else {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("done_writing ++;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().emitNewLine();
            }

            emitter().emitNewLine();
            emitter().emit("if (done_writing == %d) {", entity.getOutputPorts().size());
            {
                emitter().increaseIndentation();
                emitter().emit("thisActor->program_counter = 0;");
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("thisActor->program_counter = 2;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            emitter().emit("if (tokens_written > 0) {");
            {
                emitter().increaseIndentation();
                emitter().emit("ART_ACTION_ENTER(PRODUCE, 1);");
                emitter().emit("ART_ACTION_EXIT(PRODUCE, 1);");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emit("goto YIELD;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().emitNewLine();

        emitter().emit("YIELD: {");
        {
            emitter().increaseIndentation();
            emitter().emit("ART_ACTION_SCHEDULER_EXIT(%d, %d);", entity.getInputPorts().size(),
                    entity.getOutputPorts().size());
            emitter().emit("return result;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getDeadlockCheck(String produced, String consumed, String request) {
        emitter().emit("if (%s == 0 && %s == 0 && %s > 0)", produced, consumed, request);
        {
            emitter().increaseIndentation();

            emitter().emit("runtimeError(pBase, \"Potential device deadlock\\n\");");
            emitter().decreaseIndentation();

        }
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
            emitter().emit( "default: OCL_ERR(\"invalid plink state\");");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().emit("S0 : // Init state\n" +
                "{\n" +
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
                "S3 : // Transmited, must receive\n" +
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
                "  OCL_ERR(\"Potential device deadlock!\\n\");\n" +
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
                "  OCL_MSG(\"Retrying kernel with no inputs\\n\")\n" +
                "  thisActor->program_counter = 3;\n" +
                "  ART_EXEC_TRANSITION(transmit);\n" +
                "  goto YIELD;\n" +
                "}");

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

            if (isSimulated())
                simulationScheduler(name, entity);
            else
                deviceScheduler(name, entity);

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getSimulatorPinPeek(PortDecl port, int index) {
        emitter().emit("for (std::size_t i = 0; i < tokens_count_%s; i++)", port.getName());
        {
            emitter().increaseIndentation();
            String type = backend().typesEval().type(backend().types().declaredPortType(port));
            emitter().emit("thisActor->buffer_%s[i] = pinPeek_%s(IN%d_%1$s, i);", port.getName(), type, index);
            emitter().decreaseIndentation();
        }

        emitter().emitNewLine();
    }

    default void getSimulatorPinWrite(PortDecl port, int index) {

        emitter().emit("for (std::size_t i = thisActor->buffer_offset_%s; i < thisActor->buffer_offset_%1$s + to_write_%1$s; i++)", port.getName());
        {
            emitter().increaseIndentation();
            String type = backend().typesEval().type(backend().types().declaredPortType(port));
            emitter().emit("pinWrite_%s(OUT%d_%s, thisActor->buffer_%3$s[i]);", type, index, port.getName());
            emitter().decreaseIndentation();
        }
        emitter().emitNewLine();

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

            getTransmitAction(instanceName, entity);
            emitter().emitNewLine();


            getReceiveAction(instanceName, entity);
            emitter().emitNewLine();


            getWriteAction(instanceName, entity);
            emitter().emitNewLine();

    }

    default void conditionDefinitions(String instanceName, Entity entity) {

        getInputCondition(instanceName, entity);
        emitter().emitNewLine();

        getOutputCondition(instanceName, entity);
        emitter().emitNewLine();

        getRetryCondition(instanceName, entity);
        emitter().emitNewLine();

        getDeadlockCondition(instanceName, entity);
        emitter().emitNewLine();

    }

    default void getInputCondition(String instanceName, Entity entity) {

        emitter().emit("ART_CONDITION(input_condition, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();
            for(PortDecl input: entity.getInputPorts()) {
                String type = typeseval().type(types().declaredPortType(input));
                emitter().emit("thisActor->input_ports[%d].usable = pinAvailIn_%s(IN%1$d_%s);",
                        entity.getInputPorts().indexOf(input), type, input.getName());
            }
            emitter().emitNewLine();
            emitter().emit("bool cond = false;");
            emitter().emit("for (auto& p : thisActor->input_ports) {");
            {
                emitter().increaseIndentation();

                emitter().emit("if (p.usable > 0)");
                {
                    emitter().increaseIndentation();
                    emitter().emit("cond = true;");
                    emitter().decreaseIndentation();
                }

                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            emitter().emitNewLine();
            emitter().emit("return cond;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getOutputCondition(String instanceName, Entity entity) {
        emitter().emit("ART_CONDITION(output_condition, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();
            for(PortDecl output: entity.getOutputPorts()) {
                String type = typeseval().type(types().declaredPortType(output));
                emitter().emit("thisActor->output_ports[%d].usable = pinAvailOut_%s(OUT%1$d_%s);",
                        entity.getOutputPorts().indexOf(output), type, output.getName());
            }
            emitter().emitNewLine();
            emitter().emit("bool cond = false;");
            emitter().emit("for (auto& p : thisActor->output_ports) {");
            {
                emitter().increaseIndentation();

                emitter().emit("if (p.usable > 0)");
                {
                    emitter().increaseIndentation();
                    emitter().emit("cond = true;");
                    emitter().decreaseIndentation();
                }

                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            emitter().emitNewLine();
            emitter().emit("return cond;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
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

    default void getDeadlockCondition(String instanceName, Entity entity) {
        emitter().emit("ART_CONDITION(deadlock_check, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();

            emitter().emit("bool cond = false;");
            emitter().emit("return cond;");

            emitter().decreaseIndentation();

        }
        emitter().emit("}");
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
            emitter().emitNewLine();

            emitter().emit("ART_ACTION_EXIT(transmit, 0);");

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
    default void getWriteAction(String instanceName, Entity entity) {

        emitter().emit("ART_ACTION(write, ActorInstance_%s) { ", instanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("ART_ACTION_ENTER(write, 2);");

            emitter().emit("thisActor->dev->waitForReadBuffers();");

            emitter().emit("{");
            {
                emitter().increaseIndentation();

                emitter().emit("std::array<uint32_t, 2> avail_space;");
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




}
