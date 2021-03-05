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

import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;


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
        return backend().typeseval();
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
//        portsIO(entity);

        // -- Context
        actionContext(instanceName, entity);


        // -- State
        instanceState(instanceName, entity);

        // -- Prototypes
        prototypes(instanceName, entity);

        // -- Port description
        portDescription(instanceName, entity);


        // -- setParam
//        if (isSimulated())
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

        emitter().emit("#include <memory>");
        emitter().emit("#include \"plink.h\"");

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

            if (isSimulated()) {
                emitter().emit("std::unique_ptr<sim_device::PLink> plink;");
            } else {
                emitter().emit("std::unique_ptr<ocl_device::PLink> plink;");
            }
            emitter().emit("char *profile_file_name;");
            emitter().emit("int vcd_trace_level; // simulation only");

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


    default void constructorDefinition(String name, Entity entity) {

        emitter().emit("// -- Constructor definition");

        String actorInstanceName = "ActorInstance_" + name;


        emitter().emit("static void %s_constructor(AbstractActorInstance *pBase) {", actorInstanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("%s *thisActor = (%1$s *) pBase;", actorInstanceName);
            String deviceNameSpace = "ocl_device";

            if (isSimulated())
                deviceNameSpace = "sim_device";
            emitter().emit("using InputInfo = %s::PLink::PortInfo<LocalInputPort>;", deviceNameSpace);
            emitter().emit("using OutputInfo = %s::PLink::PortInfo<LocalOutputPort>;", deviceNameSpace);

            emitter().emitNewLine();

            // -- build the input and output ports
            emitter().emit("// -- input port builder");
            emitter().emit("std::vector<InputInfo> inputs_builder;");

            emitter().emit("// -- output port builder");
            emitter().emit("std::vector<OutputInfo> outputs_builder;");

            for (PortDecl port : entity.getInputPorts()) {
                String type = backend().typeseval().type(backend().types().declaredPortType(port));
                emitter().emit("inputs_builder.emplace_back(\"%s\", sizeof(%s));",
                        port.getName(), type);
            }

            for (PortDecl port : entity.getOutputPorts()) {
                String type = backend().typeseval().type(backend().types().declaredPortType(port));
                emitter().emit("outputs_builder.emplace_back(\"%s\", sizeof(%s));",
                        port.getName(), type);
            }


            // -- build the plink object

            emitter().emit("// -- construct the plink object");
            emitter().emit("// -- the device object");

            emitter().emit("thisActor->plink = std::make_unique<%s::PLink>(", deviceNameSpace);
            {
                emitter().increaseIndentation();
                String kernelID = backend().task().getIdentifier().getLast().toString() + "_kernel";
                emitter().emit("inputs_builder,  // -- inputs");
                emitter().emit("outputs_builder,  // -- outputs");
                emitter().emit("%d,  // -- number of external mems", 0);
                emitter().emit("\"%s\",  // -- kernel name", kernelID);
                if (isSimulated()) {
                    emitter().emit("thisActor->profile_file_name, // -- profile dump file");
                    emitter().emit("thisActor->vcd_trace_level // -- vcd trace level");
                } else {
                    emitter().emit("\"%s\",  // -- kernel dir", "xclbin");
                    emitter().emit("false   // -- stat collection");
                }

                emitter().decreaseIndentation();
            }
            emitter().emit(");");

            // -- allocate inputs

            emitter().emitNewLine();

            emitter().emit("// -- input/output allocation");

            for (PortDecl port : entity.getInputPorts()) {
                int ix = entity.getInputPorts().indexOf(port);
                String type = backend().typeseval().type(backend().types().declaredPortType(port));
                if (type == "bool") {
                    emitter().emit("OCL_ASSERT(sizeof(bool) == 1, \"sizeof(bool) is not 1 byte!\\n\");");
                }
                emitter().emit("thisActor->plink->allocateInput(inputs_builder[%d].name, thisActor->base.input[%1$d].capacity * sizeof(%s));", ix, type);
            }

            for (PortDecl port : entity.getOutputPorts()) {
                int ix = entity.getOutputPorts().indexOf(port);
                String type = backend().typeseval().type(backend().types().declaredPortType(port));
                if (type == "bool") {
                    emitter().emit("OCL_ASSERT(sizeof(bool) == 1, \"sizeof(bool) is not 1 byte!\\n\");");
                }
                emitter().emit("thisActor->plink->allocateOutput(outputs_builder[%d].name, thisActor->base.output[%1$d].capacity * sizeof(%s));", ix, type);
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
        String actorInstanceName = "ActorInstance_" + name;


        emitter().emit("static void %s_destructor(AbstractActorInstance *pBase) {", actorInstanceName);
        {
            emitter().increaseIndentation();

            emitter().emit("%s *thisActor = (%1$s *) pBase;", actorInstanceName);
            emitter().emitNewLine();


            emitter().emit("printf(\"PLink trip count = %%lu\\n\", thisActor->plink->getTripCount());");

            if (!isSimulated()) {
                emitter().emit("if (thisActor->profile_file_name != NULL)");
                {
                    emitter().increaseIndentation();
                    emitter().emit("thisActor->plink->dumpStats(thisActor->profile_file_name);");
                    emitter().decreaseIndentation();;
                }
            }

            emitter().emit("thisActor->plink->terminate();");


            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
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

            emitter().emit("auto action = thisActor->plink->actionScheduler(pBase);");

            emitter().emit("return result;");

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




    default void getInputCondition(String instanceName, Entity entity) {

        emitter().emit("ART_CONDITION(input_condition, ActorInstance_%s) {", instanceName);
        {
            emitter().increaseIndentation();
            emitter().emit("uint32_t avail_in = 0;");
            emitter().emit("bool cond = false;");
            for (PortDecl input : entity.getInputPorts()) {
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
            for (PortDecl output : entity.getOutputPorts()) {
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

}
