package ch.epfl.vlsc.wsim.backend.emitters;

import ch.epfl.vlsc.compiler.PartitionedCompilationTask;
import ch.epfl.vlsc.platformutils.Emitter;

import ch.epfl.vlsc.wsim.backend.WSimBackend;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;

import org.multij.MultiJ;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.ValueParameter;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Optional;

@Module
public interface NetworkBuilder {

    @Binding(BindingKind.INJECTED)
    WSimBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }


    default boolean isHardwarePartition(Instance instance) {
        PartitionedCompilationTask.PartitionKind part =
                PartitionedCompilationTask.getPartitionKind(instance);
        return part == PartitionedCompilationTask.PartitionKind.HW;
    }

    default String plinkInstance() {
        return "inst_system_plink_0";
    }
    default boolean isHardwareInputBuffer(Connection connection) {
        Optional<Instance> source = backend().channels().sourceInstance(connection);
        Optional<Instance> target = backend().channels().targetInstance(connection);
        if (!source.isPresent() || !target.isPresent()) {
            backend().context().getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Networks with dangling input or" +
                            "output is not supported. Make sure the top CAL network has no external inputs " +
                            "or outputs")
            );
        }
        return isHardwarePartition(target.get()) && !isHardwarePartition(source.get());
    }
    default boolean isHardwareOutputBuffer(Connection connection) {
        Optional<Instance> source = backend().channels().sourceInstance(connection);
        Optional<Instance> target = backend().channels().targetInstance(connection);
        if (source.isPresent() == false || target.isPresent() == false) {
            backend().context().getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Networks with dangling input or" +
                            "output is not supported. Make sure the top CAL network has no external inputs " +
                            "or outputs")
            );
        }
        return !isHardwarePartition(target.get()) && isHardwarePartition(source.get());
    }

    default void emitMacros() {
        emitter().emit("" +
                "#define STREAMBLOCKS_ACTOR_INST(ACTOR_NAME) inst_##ACTOR_NAME\n" +
                "#define STREAMBLOCKS_MAKE_ACTOR(ACTOR_NAME, ATTRS)                             \\\n" +
                "  auto STREAMBLOCKS_ACTOR_INST(ACTOR_NAME) =                                   \\\n" +
                "      ::wsim::make_actor<streamblocks::generated::ACTOR_NAME>(                 \\\n" +
                "          #ACTOR_NAME, ATTRS)\n" +
                "\n" +
                "#define STREAMBLOCKS_BUFFER_NAME(source, sourceport, target, targetport,       \\\n" +
                "                                 index)                                        \\\n" +
                "  buff_##source##_##sourceport##_##target##_##targetport##_##index\n" +
                "#define STREAMBLOCKS_PLINK_PORT_NAME(source, sourceport, target, targetport)   \\\n" +
                "  source##_##sourceport##_##plink##_##target##_##targetport\n" +
                "\n" +
                "#define STREAMBLOCKS_BUFFER(source, sourceport, target, targetport, index,     \\\n" +
                "                            TYPE)                                              \\\n" +
                "  auto STREAMBLOCKS_BUFFER_NAME(source, sourceport, target, targetport,        \\\n" +
                "                                index) =                                       \\\n" +
                "      ::wsim::make_buffer<TYPE>(::wsim::getFifoSize(                           \\\n" +
                "          #source, #sourceport, #target, #targetport, xml_connections, 4096))\n" +
                "\n" +
                "#define STREAMBLOCKS_MAKE_LOCAL_BUFFER(source, sourceport, target, targetport, \\\n" +
                "                                       TYPE)                                   \\\n" +
                "  STREAMBLOCKS_BUFFER(source, sourceport, target, targetport, local, TYPE);\n" +
                "\n" +
                "#define STREAMBLOCKS_MAKE_CROSSING_BUFFER(source, sourceport, target,          \\\n" +
                "                                          targetport, TYPE)                    \\\n" +
                "  STREAMBLOCKS_BUFFER(source, sourceport, target, targetport, original, TYPE); \\\n" +
                "  STREAMBLOCKS_BUFFER(source, sourceport, target, targetport, proxy, TYPE);\n");

    }
    default void buildNetwork(Network network, Path targetPath) {

        Path filePath = targetPath.resolve(backend().task().getIdentifier().getLast().toString() + ".cpp");
        emitter().open(filePath);

        emitter().emit("#include <wsim.h>");
        emitter().emit("#include <wsim-natives.h>");
        for(Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal())
                emitter().emit("#include \"%s.h\"", instance.getInstanceName());
        }
        emitMacros();
        emitter().emit("std::vector<std::unique_ptr<::wsim::SequentialPartition>> " +
                "buildNetwork(const std::unique_ptr<wsim::cal::options::SimulationConfig> &cfg) {");
        {
            emitter().increaseIndentation();

            emitter().emit("// read the xml file");

            emitter().emit("::wsim::XmlConfigurationReader reader(cfg->config_path);");
            emitter().emit("auto xml_connections = reader.readConnections();");
            emitter().emit("auto partitions = reader.readPartitions();");
            emitter().emit("auto profile = reader.readProfile();");

            emitter().emit("// allocate instances");
            buildInstances(network);

            emitter().emit("// allocate buffers");
            allocateBuffers(network);

            emitter().emit("// connect ports");
            connectPorts(network);

            emitter().emit("// collect instances");
            emitter().emit("::std::map<::std::string, ::std::unique_ptr<::wsim::ActorBase>> instance_collection;");

            for(Instance instance : network.getInstances()) {
                emitter().emit("instance_collection.emplace(::std::make_pair(\"%s\", ::std::move(STREAMBLOCKS_ACTOR_INST(%1$s))));",
                        instance.getInstanceName());
            }


            emitter().emit("auto fpga_scheduler = ::wsim::makeFPGAScheduler(instance_collection," +
                    " partitions->fpga_partition, profile);");
            emitter().emit("using PLinkActorClass = ::wsim::PLink<decltype(plink_input_ports), decltype(plink_output_ports)>;");
            emitter().emit("auto plink_instance = ::wsim::make_actor<PLinkActorClass>(plink_input_ports, plink_output_ports, " +
                    "::std::move(fpga_scheduler), cfg->fpga_freq, cfg->cpu_freq, 1000.0 , ::wsim::AttributeList::from(\"verbose\", cfg->verbose));");

            // add the plink to the instances
            emitter().emit("instance_collection.emplace(::std::make_pair(plink_instance->getInstanceName(), ::std::move(plink_instance)));");

            emitter().emitNewLine();
            emitter().emit("// -- thread schedulers");
            emitter().emit("auto threads_schedulers = makeThreadSchedulers(instance_collection, partitions->threads_partitions, profile);");

            emitter().emit("return threads_schedulers;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().close();
    }

    default void buildInstances(Network network) {

        emitter().emit("// -- build instances");


        ParamToAttributeType converter = MultiJ.from(ParamToAttributeType.class).instance();
        for(Instance instance : network.getInstances()) {
//            if (instance.getValueParameters().)


            String attributes = "::wsim::AttributeList::empty()";
            for(ValueParameter param : instance.getValueParameters()) {
                Type pType = backend().types().type(param.getValue());
                try {
                    String typeStr = converter.convert(pType);
                    attributes = attributes + ".add(\"" + param.getName() + "\", " +
                            backend().expressions().evaluate(param.getValue()) + ")";

                } catch (CompilationException e) {
                    backend().context().getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR,
                            "Can not set the value parameter of type " + backend().typeseval().type(pType) +
                                    " for instance " + instance.getInstanceName() + " currently only " +
                                    "int, string and bool types are supported"));
                }
            }
            GlobalEntityDecl decl = backend().globalnames().entityDecl(instance.getEntityName(), true);

            if (!decl.getExternal())
                emitter().emit("STREAMBLOCKS_MAKE_ACTOR(%s, %s);", instance.getInstanceName(), attributes);
            else {
                emitter().emit("// -- external actor");
                String externalName = "::" + String.join("::", instance.getEntityName().parts());
                emitter().emit("auto STREAMBLOCKS_ACTOR_INST(%s) = ::wsim::make_actor<%s>(\"%1$s\", %s);",
                        instance.getInstanceName(), externalName, attributes);
            }

        }

    }
    default void allocateBuffers(Network network) {

        emitter().emit("// -- allocate FIFO buffers");
        ImmutableList.Builder<Connection> plinkInputs = ImmutableList.builder();
        ImmutableList.Builder<Connection> plinkOutputs = ImmutableList.builder();
        for(Connection connection : network.getConnections()) {
            try{
                boolean isHardwareOutput = isHardwareOutputBuffer(connection);
                boolean isHardwareInput = isHardwareInputBuffer(connection);

                if (isHardwareInput)
                    plinkInputs.add(connection);
                if (isHardwareOutput)
                    plinkOutputs.add(connection);

                String constructorMacro = isHardwareOutput || isHardwareInput ?
                        "STREAMBLOCKS_MAKE_CROSSING_BUFFER" : "STREAMBLOCKS_MAKE_LOCAL_BUFFER";

                String typeStr = backend().typeseval().type(backend().types().connectionType(network, connection));


                emitter().emit("%s(%s, %s, %s, %s, %s);", constructorMacro,
                            connection.getSource().getInstance().get(), connection.getSource().getPort(),
                            connection.getTarget().getInstance().get(), connection.getTarget().getPort(),
                            typeStr);

            } catch (NoSuchElementException e) {
                backend().context().getReporter().report(
                        new Diagnostic(Diagnostic.Kind.ERROR, "Networks with dangling input or" +
                                "output is not supported. Make sure the top CAL network has no external inputs " +
                                "or outputs")
                );
            }

        }

        emitter().emit("// -- PLink input and output interface");
        emitPlinkPortMaker(plinkInputs.build(), true);
        emitPlinkPortMaker(plinkOutputs.build(), false);


    }

    default void connectPorts(Network network) {

            int plink_input_ix = 0;
            int plink_output_ix = 0;
            for(Connection connection : network.getConnections()) {
                try {
                    boolean isHardwareOutput = isHardwareOutputBuffer(connection);
                    boolean isHardwareInput = isHardwareInputBuffer(connection);
                    emitter().emit("{");
                    {
                        emitter().increaseIndentation();
                        if (isHardwareInput && !isHardwareOutput) {
                            // plink input interface

                            emitter().emit("auto plink_input_software = " +
                                            "::std::get<%d>(plink_input_ports.objects)->getPort();",
                                    plink_input_ix);
                            emitter().emit("::wsim::connectOutputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, original), " +
                                            "STREAMBLOCKS_ACTOR_INST(%1$s)->outputs.%2$s);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());

                            emitter().emit("::wsim::connectInputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, original), " +
                                            "plink_input_software);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());

                            emitter().emit("auto plink_input_proxy = ::std::get<%d>(plink_input_ports.objects)" +
                                    "->getProxy();", plink_input_ix);
                            emitter().emit("::wsim::connectOutputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, proxy), " +
                                            "plink_input_proxy);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());
                            emitter().emit("::wsim::connectInputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, proxy), " +
                                            "STREAMBLOCKS_ACTOR_INST(%3$s)->inputs.%4$s);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());

                            plink_input_ix ++;

                        } else if (!isHardwareInput && isHardwareOutput) {
                            // plink output interface

                            emitter().emit("auto plink_output_software = " +
                                            "::std::get<%d>(plink_output_ports.objects)->getPort();",
                                    plink_output_ix);
                            emitter().emit("::wsim::connectOutputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, original), " +
                                            "plink_output_software);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());

                            emitter().emit("::wsim::connectInputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, original), " +
                                            "STREAMBLOCKS_ACTOR_INST(%3$s)->inputs.%4$s);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());

                            emitter().emit("auto plink_output_proxy = ::std::get<%d>(plink_output_ports.objects)" +
                                    "->getProxy();", plink_output_ix);
                            emitter().emit("::wsim::connectOutputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, proxy), " +
                                            "STREAMBLOCKS_ACTOR_INST(%1$s)->outputs.%2$s);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());
                            emitter().emit("::wsim::connectInputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, proxy), " +
                                            "plink_output_proxy);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());
                            plink_output_ix ++;
                        } else {
                            // local connection
                            emitter().emit("::wsim::connectOutputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, local), " +
                                            "STREAMBLOCKS_ACTOR_INST(%1$s)->outputs.%2$s);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());
                            emitter().emit("::wsim::connectInputBuffer" +
                                            "(STREAMBLOCKS_BUFFER_NAME(%s, %s, %s, %s, local), " +
                                            "STREAMBLOCKS_ACTOR_INST(%3$s)->inputs.%4$s);",
                                    connection.getSource().getInstance().get(), connection.getSource().getPort(),
                                    connection.getTarget().getInstance().get(), connection.getTarget().getPort());
                        }

                        emitter().decreaseIndentation();
                    }
                    emitter().emit("}");

                } catch (NoSuchElementException e) {
                    backend().context().getReporter().report(
                            new Diagnostic(Diagnostic.Kind.ERROR, "Networks with dangling input or" +
                                    "output is not supported. Make sure the top CAL network has no external inputs " +
                                    "or outputs")
                    );
                }
            }

    }


    default void emitPlinkPortMaker(ImmutableList<Connection> connections, boolean isInput){

        ImmutableList.Builder<String> connectionCons = ImmutableList.builder();
        int i = 0;
        for (Connection c : connections) {
            String type = backend().typeseval().type(backend().types().connectionType(backend().task().getNetwork(),
                    c));
            String name = String.format("%s_%s_plink_%s_%s",
                    c.getSource().getInstance().get(), c.getSource().getPort(),
                    c.getTarget().getInstance().get(), c.getTarget().getPort());
            connectionCons.add(
                    String.format("(%s, %s, %d)", name, type, i++)
            );

        }
        String indentString = Emitter.makeIndentation(emitter().getIndentation() + 1);
        emitter().emit("auto plink_%1$s_ports = ::wsim::make_plink_%1$s_ports(", isInput ? "input" : "output");
        {
            emitter().increaseIndentation();
            emitter().emit("%s", String.join(",\n" + indentString,
                    connectionCons.build().map(c ->
                            "MAKE_PLINK_" + (isInput ? "INPUT" : "OUTPUT") + "_INTERFACE" + c)));
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    @Module
    interface ParamToAttributeType {

        default String convert(Type type) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Conversion not supported!"));
        }
        default String convert(StringType stringType) {
            return "String";
        }
        default String convert(IntType intType) {
            return "Integer";
        }
        default String convert(BoolType boolType) {
            return "Boolean";
        }
    }

}
