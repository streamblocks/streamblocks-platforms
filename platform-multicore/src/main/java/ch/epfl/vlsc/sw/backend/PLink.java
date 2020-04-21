package ch.epfl.vlsc.sw.backend;


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
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;


import java.nio.file.Path;

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

        throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,"plink " +
                stackTrace[2].getMethodName() + " method not implemented!"));
    }

    default TypesEvaluator typeseval() { return backend().typeseval(); }

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
    /**
     * This method generates an implementation of plink that has two actions, Transmit (TX) and Receive (RX).
     * The TX actions is initiated whenever there is data available at the inputs ports of the plink instance, and
     * then the scheduler exits. On the next scheduler, call the RX action is initiated in which the buffers are polled
     * for availability, if there is data available, the data is output.
     * @param instance
     * @throws CompilationException
     */
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
                    .resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".c");
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
        emitter().emit("#include \"device-handle.h\"");
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

    default void instanceState(String name, Entity entity) {

        emitter().emit("// -- Instance state structure");
        emitter().emit("typedef struct{");
        {
            emitter().increaseIndentation();
            emitter().emit("AbstractActorInstance base;");
            emitter().emit("int32_t program_counter;");


            emitter().emit("// -- device handle object");
            emitter().emit("DeviceHandle_t dev;");
            emitter().emit("%s cl_buffer_size;", defaultSizeType());

            // -- request size
            for (PortDecl port: entity.getInputPorts()) {
                emitter().emit("uint32_t %s_request_size;", port.getName());
            }
            emitter().emitNewLine();

            // -- buffers
            for (PortDecl port: Stream.concat(
                    entity.getInputPorts().stream(),
                    entity.getOutputPorts().stream()).collect(Collectors.toList())) {
                emitter().emit("// -- buffer and transaction size for port %s", port.getName());
                String type = typeseval().type(types().declaredPortType(port));
                emitter().emit("%s *%s_buffer;", type, port.getName());
                emitter().emit("%s *%s_size;", defaultIntType(), port.getName());
            }

            for (PortDecl port: entity.getOutputPorts()) {
                emitter().emit("%s %s_offset;", defaultSizeType(), port.getName());
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("} ActorInstance_%s;", backend().instaceQID(name, "_"));
        emitter().emitNewLine();
    }

    default void prototypes(String name, Entity entity) {

//        emitter().emit("// -- TX prototype");
//        emitter().emit("ART_ACTION(%s, %s);", TxName(), name);
//        emitter().emit("// -- RX prototype");
//        emitter().emit("ART_ACTION(%s, %s);", RxName(), name);
        String instanceQID = backend().instaceQID(name, "_");
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

        String instanceQID = backend().instaceQID(name, "_");
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

    default void constructorDefinition(String name, Entity entity) {

        emitter().emit("// -- Constructor definition");

        String actorInstanceName = "ActorInstance_" + backend().instaceQID(name, "_");
        PartitionHandle handle = ((PartitionLink) entity).getHandle();

        emitter().emit("static void %s_constructor(AbstractActorInstance *pBase) {", actorInstanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("%s *thisActor = (%1$s *) pBase;", actorInstanceName);

            emitter().emit("thisActor->program_counter = 0;");
            emitter().emit("thisActor->cl_buffer_size = (1 << 20); // 4MiB CL buffers");
            emitter().emitNewLine();

            String kernelID = backend().task().getIdentifier().getLast().toString() + "_kernel";


            // -- Constructor
            emitter().emit("// -- Construct the FPGA device handle");
            String consName = backend().devicehandle().methodName(handle, handle.getConstructor());
            emitter().emit("%s(&thisActor->dev, \"%s\", \"xilinx_kcu1500_dynamic_5_0\", \"bin/xclbin\", false);",
                    consName, kernelID);
            emitter().emitNewLine();


            // -- createCLBuffers
            emitter().emit("// -- allocate CL buffers");
            emitter().emit("%s(&thisActor->dev, thisActor->cl_buffer_size);",
                    getMethod(handle, "createCLBuffers"));
            emitter().emitNewLine();



            // -- allocate host buffers
            emitter().emit("// -- allocate host buffers");
            for (PortDecl port: Stream.concat(
                    entity.getInputPorts().stream(), entity.getOutputPorts().stream()).collect(Collectors.toList())) {
                String type = typeseval().type(types().declaredPortType(port));
                emitter().emit("thisActor->%s_buffer = (%s *) aligned_alloc(MEM_ALIGNMENT, " +
                        "thisActor->cl_buffer_size * sizeof(%2$s));", port.getName(), type);
                String setPtrName = getMethod(handle, "set_" + port.getName() + "_buffer_ptr");
                emitter().emit("%s(&thisActor->dev, thisActor->%s_buffer);",
                        setPtrName, port.getName());

                emitter().emit("thisActor->%s_size = (%s *) aligned_alloc(MEM_ALIGNMENT, sizeof(%2$s));",
                        port.getName(), defaultIntType());
                String setSizePtrName = getMethod(handle, "set_" + port.getName() + "_size_ptr");
                emitter().emit("%s(&thisActor->dev, thisActor->%s_size);",
                        setSizePtrName, port.getName());
            }

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
    default void destructorDefinition(String name, Entity entity) {
        emitter().emit("// -- destructor definition");
        String actorInstanceName = "ActorInstance_" + backend().instaceQID(name, "_");
        PartitionHandle handle = ((PartitionLink) entity).getHandle();

        emitter().emit("static void %s_destructor(AbstractActorInstance *pBase) {", actorInstanceName);
        {
            emitter().increaseIndentation();

            emitter().emit("%s *thisActor = (%1$s *) pBase;", actorInstanceName);
            emitter().emitNewLine();
            // -- device destructor
            String decons = backend().devicehandle().methodName(handle, handle.getDestructor());
            emitter().emit("%s(&thisActor->dev);", decons);

            emitter().emitNewLine();
            // -- host buffer deallocation
            for(PortDecl port: Stream.concat(
                    entity.getInputPorts().stream(), entity.getOutputPorts().stream()).collect(Collectors.toList())){
                emitter().emit("free(thisActor->%s_buffer);", port.getName());
                emitter().emit("free(thisActor->%s_size);", port.getName());
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }
    default void scheduler(String name, Entity entity) {

        PartitionHandle handle = ((PartitionLink) entity).getHandle();

        emitter().emit("// -- scheduler definitions");
        emitter().emit("static const int exitcode_block_any[3] = {1, 0, 1};");
        emitter().emitNewLine();
        String actorInstanceName = "ActorInstance_" + backend().instaceQID(name, "_");
        emitter().emit("ART_ACTION_SCHEDULER(%s_scheduler){", actorInstanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("const int *result = exitcode_block_any;");
            emitter().emit("static const int exitCode[] = {EXITCODE_BLOCK(1), 0 , 1};");
            emitter().emitNewLine();
            emitter().emit("%s *thisActor = (%1$s *) pBase;", actorInstanceName);
            emitter().emitNewLine();
            emitter().emit("ART_ACTION_SCHEDULER_ENTER(%d, %d)", entity.getInputPorts().size(),
                    entity.getOutputPorts().size());
            emitter().emitNewLine();

            emitter().emit("switch (thisActor->program_counter) {");
            {
                emitter().increaseIndentation();
                if (entity.getInputPorts().size() > 0) {
                    emitter().emit("case 0: goto CHECK;");
                } else {
                    emitter().emit("case 0: goto TX;");
                }
                emitter().emit("case 2: goto RX;", entity.getInputPorts().size() + 1);
                emitter().emit("case 3: goto WRITE;", entity.getInputPorts().size() + 2);
                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            if (entity.getInputPorts().size() > 0 ){
                emitter().emit("CHECK: {");
                {
                    emitter().increaseIndentation();
                    ImmutableList.Builder<String> tokens = ImmutableList.builder();
                    for(PortDecl port: entity.getInputPorts()) {

                        String type = typeseval().type(types().declaredPortType(port));
                        Boolean last = entity.getInputPorts().size() - 1 == entity.getInputPorts().indexOf(port);
                        tokens.add("tokens_" + port.getName());
                        emitter().emit("uint32_t tokens_%s = pinAvailIn_%s(IN%d_%1$s); ", port.getName(), type, entity.getInputPorts().indexOf(port));

                    }
                    emitter().emitNewLine();
                    String canTransmit = String.join(" && ",
                            tokens.build().stream().map(t -> "(" + t + " > 0)").collect(Collectors.toList()));
                    emitter().emit("if (%s) {", canTransmit);
                    {
                        emitter().increaseIndentation();
                        emitter().emit("thisActor->program_counter = 1;");
                        emitter().emit("goto TX;");
                        emitter().decreaseIndentation();
                    }

                    emitter().emit("} else {");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("thisActor->program_counter = 0;");
                        emitter().emit("goto YIELD;");
                        emitter().decreaseIndentation();
                    }
                    emitter().emit("}");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            }

            emitter().emit("TX: { // -- Transmit to FPGA memory");
            {
                emitter().increaseIndentation();
                emitter().emit("ART_ACTION_ENTER(TX, 0);");
                for (PortDecl port: entity.getInputPorts()) {
                    String type = typeseval().type(types().declaredPortType(port));
                    String setReq = getMethod(handle, "set_" + port.getName() + "_request_size");

                    // --set request size
                    emitter().emit("// -- set request size");
                    emitter().emit("thisActor->%s_request_size = pinAvailIn_%s(IN%d_%1$s);",
                            port.getName(), type, entity.getInputPorts().indexOf(port));

                    emitter().emit("%s (&thisActor->dev, thisActor->%s_request_size);",
                            setReq, port.getName());
                    emitter().emitNewLine();

                    // -- peek the buffer
                    emitter().emit("pinPeekRepeat_%s(IN%d_%s, thisActor->%3$s_buffer," +
                            " thisActor->%3$s_request_size);",
                            type, entity.getInputPorts().indexOf(port), port.getName());
                    emitter().emitNewLine();

                }


                // -- execute the kernel
                emitter().emit("%s(&thisActor->dev);", getMethod(handle, "run"));
                emitter().emitNewLine();

                emitter().emit("thisActor->program_counter = 2;");
                emitter().emit("ART_ACTION_ENTER(TX, 0);");
                emitter().emit("goto YIELD;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");


            emitter().emit("RX: { // -- Receive from FPGA");
            {
                emitter().increaseIndentation();
                emitter().emit("ART_ACTION_ENTER(RX, 1);");
                //-- wait on device
                emitter().emit("%s(&thisActor->dev);", getMethod(handle, "waitForDevice"));

                emitter().emitNewLine();

                // -- consume tokens
                emitter().emit("// -- Consume on behalf of device");
                for (PortDecl port: entity.getInputPorts()) {
                    String type = typeseval().type(types().declaredPortType(port));
                    emitter().emit("if (thisActor->%s_size[0] > 0)", port.getName());
                    emitter().emit("\tpinConsumeRepeat_%s(IN%d_%s, thisActor->%3$s_size[0]);",
                            type, entity.getInputPorts().indexOf(port), port.getName());
                }
                emitter().emitNewLine();
                for (PortDecl port: entity.getOutputPorts()) {
                    emitter().emit("thisActor->%s_offset = 0;", port.getName());
                }
                emitter().emitNewLine();
                emitter().emit("thisActor->program_counter = 3;", entity.getInputPorts().size() + 2);
                emitter().emit("ART_ACTION_EXIT(RX, 1);");
                emitter().emit("goto WRITE;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emit("WRITE: {// -- retry reading");
            {
                emitter().increaseIndentation();
                emitter().emit("ART_ACTION_ENTER(WRITE, 2);");
                emitter().emit("uint32_t done_reading = 0;");
                for (PortDecl port: entity.getOutputPorts()) {
                    String type = typeseval().type(types().declaredPortType(port));
                    String offset = "thisActor->" + port.getName() + "_offset";
                    String readSize = "thisActor->" + port.getName() + "_size[0]";
                    String outputPort = String.format("OUT%d_%s", entity.getOutputPorts().indexOf(port),
                            port.getName());
                    String pinAvailOut = "pinAvailOut_" + type + "(" + outputPort + ")";
                    String remain = port.getName() + "_remain";
                    String offsetBuffer =
                            String.format("&thisActor->%s_buffer[thisActor->%1$s_offset]", port.getName());
                    String toWrite = port.getName() + "_to_write";
                    // -- size_t remain = readSize - offset
                    emitter().emit("%s %s = %s - %s;", defaultSizeType(), remain,
                            readSize, offset);
                    emitter().emit("uint32_t %s = MIN(%s, %s);", toWrite, remain, pinAvailOut);
                    emitter().emit("if (%s > 0) { // -- if there are tokens remaining", remain);
                    {
                        emitter().increaseIndentation();
                        emitter().emit("pinWriteRepeat_%s(%s, %s, %s);", type, outputPort, offsetBuffer,
                                toWrite);
                        emitter().emit("%s += %s;", offset, toWrite);
                        emitter().emit("if (%s == %s) // this port is done", offset, readSize);
                            emitter().emit("\tdone_reading ++;");

                        emitter().decreaseIndentation();
                    }
                    emitter().emit("} else {");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("done_reading ++;");
                        emitter().decreaseIndentation();
                    }
                    emitter().emit("}");
                    emitter().emitNewLine();

                }
                emitter().emit("if (done_reading == %d) {", entity.getOutputPorts().size());
                {
                    emitter().increaseIndentation();
                    emitter().emit("thisActor->program_counter = 0;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("} else {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("thisActor->program_counter = 3;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().emit("ART_ACTION_EXIT(WRITE, 2);");
                emitter().emit("goto YIELD;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            emitter().emit("YIELD: {");
            {
                emitter().increaseIndentation();
                emitter().emit("ART_ACTION_SCHEDULER_EXIT(%d, %d)", entity.getInputPorts().size(),
                        entity.getOutputPorts().size());
                emitter().emit("return result;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

}
