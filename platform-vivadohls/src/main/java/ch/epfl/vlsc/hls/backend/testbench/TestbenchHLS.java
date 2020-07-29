package ch.epfl.vlsc.hls.backend.testbench;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface TestbenchHLS {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }


    default void defineIncludes(String instanceName) {
        backend().includeSystem("fstream");
        backend().includeSystem("sstream");
        backend().includeSystem("iostream");
        backend().includeSystem("string");
        backend().includeSystem("stdint.h");
        backend().includeUser("hls_stream.h");
        if (backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
            backend().includeSystem("ap_int.h");
        }
        emitter().emitNewLine();
        backend().includeUser(instanceName + ".h");
        emitter().emitNewLine();
    }

    default void networkIncludes(List<Instance> instances) {
        backend().includeSystem("fstream");
        backend().includeSystem("sstream");
        backend().includeSystem("iostream");
        backend().includeSystem("string");
        backend().includeSystem("stdint.h");
        backend().includeUser("hls_stream.h");
        if (backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
            backend().includeSystem("ap_int.h");
        }
        emitter().emitNewLine();

        instances.forEach(i -> {
            backend().includeUser(i.getInstanceName() + ".h");
        });

        emitter().emitNewLine();
    }

    default void generateInstanceTestbench(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add entity to box
        backend().entitybox().set(entity);

        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSrcTb(backend().context()).resolve("tb_" + instance.getInstanceName() + ".cpp");
        emitter().open(instanceTarget);

        // -- Includes
        defineIncludes(instance.getInstanceName());

        // -- Instance
        emitter().emit("// -- Instance prototype");
        emitter().emit("int %s(%s);", instance.getInstanceName(), backend().instance().entityPorts(instance.getInstanceName(), entity instanceof ActorMachine, true));
        emitter().emitNewLine();

        emitter().emit("// -- HLS Testbench");
        emitter().emit("int main(){");
        {
            emitter().increaseIndentation();
            emitter().emit("// -- File Streams");
            // -- Input Streams
            entity.getInputPorts().forEach(p -> openStreams(p, instance.getInstanceName()));

            // -- Output Streams
            entity.getOutputPorts().forEach((p -> openStreams(p, instance.getInstanceName())));

            // -- Input channels
            if (!entity.getInputPorts().isEmpty()) {
                emitter().emit("// -- Input Channels");
                entity.getInputPorts().forEach(this::channels);
            }

            emitter().emitNewLine();

            // -- Output channels
            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// -- Output Channels");
                entity.getOutputPorts().forEach(this::channels);
            }

            emitter().emitNewLine();

            // -- Queue reference
            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// -- Queue reference");
                entity.getOutputPorts().forEach(this::refQueues);
            }

            emitter().emitNewLine();

            // -- Write tokens to stream
            if (!entity.getInputPorts().isEmpty()) {
                emitter().emit("// -- Write tokens to stream");
                entity.getInputPorts().forEach(p -> writeToStream(p, null));
            }

            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// -- Store output tokens to the reference queue");
                entity.getOutputPorts().forEach(this::storeToQueueRef);
            }

            // -- Output counters
            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// -- Output Counters");
                entity.getOutputPorts().forEach(this::outputCounters);
                emitter().emitNewLine();
            }

            // -- End of execution
            emitter().emit("// -- End of execution");
            emitter().emit("bool end_of_execution = false;");
            emitter().emitNewLine();

            // -- Running
            emitter().emit("// -- Execute instance under test");
            emitter().emit("while(!end_of_execution) {");
            {
                emitter().increaseIndentation();

                emitter().emit("IO_%s io_%1$s;", instance.getInstanceName());
                emitter().emitNewLine();

                for (PortDecl port : entity.getInputPorts()) {
                    emitter().emit("io.%s_count = %1$s.size();", port.getName());
                    emitter().emit("io.%s_peek = %1$s._data[0];", port.getName());
                }
                for (PortDecl port : entity.getOutputPorts()) {
                    emitter().emit("io.%s_count = 0;", port.getName());
                    emitter().emit("io.%s_size = 512;", port.getName());
                }
                emitter().emitNewLine();


                List<String> ports = new ArrayList<>();
                // -- External memories
                ports.addAll(
                        backend().externalMemory()
                                .getExternalMemories(entity).map(v -> backend().externalMemory().name(instance, v)));

                ports.addAll(entity.getInputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
                ports.addAll(entity.getOutputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
                emitter().emit("int ret = %s(%s, io);", instance.getInstanceName(), String.join(", ", ports));
                emitter().emitNewLine();

                // -- Output counters
                if (!entity.getOutputPorts().isEmpty()) {
                    entity.getOutputPorts().forEach(p -> compareOutput(p, null));
                }

                emitter().emit("end_of_execution = ret == RETURN_WAIT;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            if (!entity.getOutputPorts().isEmpty()) {
                entity.getOutputPorts().forEach(this::printProduced);
                emitter().emitNewLine();
            }

            emitter().emit("return 0;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().close();

        // -- Clear boxes
        backend().entitybox().clear();
        backend().instancebox().clear();
    }

    default void generateNetworkTestbench() {
        // -- Network Id
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSrcTb(backend().context()).resolve("tb_" + identifier + ".cpp");
        emitter().open(instanceTarget);

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Includes
        networkIncludes(network.getInstances());

        // -- Instance prototypes
        emitter().emit("// -- Instance prototypes");
        for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            Entity entity = entityDecl.getEntity();
            backend().entitybox().set(entity);
            emitter().emit("int %s(%s);", instance.getInstanceName(), backend().instance().entityPorts(instance.getInstanceName(), entity instanceof ActorMachine, true));
            backend().entitybox().clear();
        }
        emitter().emitNewLine();

        emitter().emit("// -- HLS Network Testbench");
        emitter().emit("int main(){");
        {
            emitter().increaseIndentation();

            emitter().increaseIndentation();
            emitter().emit("// -- File Streams");
            // -- Input Streams
            network.getInputPorts().forEach((p -> openStreams(p, identifier)));

            // -- Output Streams
            network.getOutputPorts().forEach((p -> openStreams(p, identifier)));

            // -- Queues
            for (Connection connection : network.getConnections()) {
                String queueName = backend().vnetwork().getQueueName(connection);
                Type queueType = backend().vnetwork().getQueueType(connection);
                emitter().emit("hls::stream< %s > %s(\"%2$s\");", backend().typeseval().type(queueType), queueName);
            }
            emitter().emitNewLine();

            // -- Queue reference
            if (!network.getOutputPorts().isEmpty()) {
                emitter().emit("// -- Queue reference");
                network.getOutputPorts().forEach(this::refQueues);
            }
            emitter().emitNewLine();

            // -- Write tokens to stream
            if (!network.getInputPorts().isEmpty()) {
                emitter().emit("// -- Write tokens to stream");
                for (PortDecl port : network.getInputPorts()) {
                    Connection.End source = new Connection.End(Optional.empty(), port.getName());
                    Connection connection = network.getConnections().stream()
                            .filter(conn -> conn.getSource().equals(source))
                            .findFirst().get();
                    writeToStream(port, connection);
                }
            }

            if (!network.getOutputPorts().isEmpty()) {
                emitter().emit("// -- Store output tokens to the reference queue");
                network.getOutputPorts().forEach(this::storeToQueueRef);
            }

            // -- Output counters
            if (!network.getOutputPorts().isEmpty()) {
                emitter().emit("// -- Output Counters");
                network.getOutputPorts().forEach(this::outputCounters);
                emitter().emitNewLine();
            }


            // -- End of execution
            emitter().emit("// -- End of execution");
            emitter().emit("int end_of_execution = RETURN_WAIT;");
            emitter().emitNewLine();

            // -- Running
            emitter().emit("// -- Execute instance under test");
            emitter().emit("do {");
            {
                emitter().increaseIndentation();

                emitter().emitNewLine();

                for (Instance instance : network.getInstances()) {
                    emitter().emit("IO_%s io_%1$s;", instance.getInstanceName());
                    emitter().emitNewLine();

                    GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
                    Entity entity = entityDecl.getEntity();

                    for (PortDecl port : entity.getInputPorts()) {
                        Connection.End target = new Connection.End(Optional.of(instance.getInstanceName()), port.getName());
                        Connection connection = backend().task().getNetwork().getConnections().stream()
                                .filter(c -> c.getTarget().equals(target)).findAny().orElse(null);
                        String queueName = backend().vnetwork().queueNames().get(connection);

                        emitter().emit("io_%s.%s_count = %s.size();", instance.getInstanceName(), port.getName(), queueName);
                        emitter().emit("if(io_%s.%s_count)", instance.getInstanceName(), port.getName());
                        emitter().emit("\tio_%s.%s_peek = %s._data[0];", instance.getInstanceName(), port.getName(), queueName);
                    }
                    for (PortDecl port : entity.getOutputPorts()) {
                        String portName = port.getName();
                        Connection.End source = new Connection.End(Optional.of(instance.getInstanceName()), portName);
                        Connection connection = backend().task().getNetwork().getConnections().stream()
                                .filter(c -> c.getSource().equals(source)).findAny().orElse(null);
                        String queueName = backend().vnetwork().queueNames().get(connection);

                        emitter().emit("io_%s.%s_count = %s.size();", instance.getInstanceName(), port.getName(), queueName);
                        emitter().emit("io_%s.%s_size = %s;", instance.getInstanceName(), port.getName(), backend().channelsutils().connectionBufferSize(connection));
                    }
                    emitter().emitNewLine();


                    List<String> ports = new ArrayList<>();
                    // -- External memories
                    ports.addAll(
                            backend().externalMemory()
                                    .getExternalMemories(entity).map(v -> backend().externalMemory().name(instance, v)));

                    for (PortDecl port : entity.getInputPorts()) {
                        Connection.End target = new Connection.End(Optional.of(instance.getInstanceName()), port.getName());
                        Connection connection = backend().task().getNetwork().getConnections().stream()
                                .filter(c -> c.getTarget().equals(target)).findAny().orElse(null);
                        String queueName = backend().vnetwork().queueNames().get(connection);
                        ports.add(queueName);
                    }
                    for (PortDecl port : entity.getOutputPorts()) {
                        String portName = port.getName();
                        Connection.End source = new Connection.End(Optional.of(instance.getInstanceName()), portName);
                        Connection connection = backend().task().getNetwork().getConnections().stream()
                                .filter(c -> c.getSource().equals(source)).findAny().orElse(null);
                        String queueName = backend().vnetwork().queueNames().get(connection);
                        ports.add(queueName);
                    }

                    emitter().emit("end_of_execution = %s(%s, io_%1$s);", instance.getInstanceName(), String.join(", ", ports));
                    emitter().emitNewLine();
                }

                // -- Output counters
                if (!network.getOutputPorts().isEmpty()) {
                    for (PortDecl port : network.getOutputPorts()) {
                        Connection.End target = new Connection.End(Optional.empty(), port.getName());
                        Connection connection = network.getConnections().stream()
                                .filter(conn -> conn.getTarget().equals(target))
                                .findFirst().get();
                        compareOutput(port, connection);
                    }
                }

                emitter().decreaseIndentation();
            }
            emitter().emit("} while(end_of_execution != RETURN_EXECUTED);");
            emitter().emitNewLine();

            if (!network.getOutputPorts().isEmpty()) {
                network.getOutputPorts().forEach(this::printProduced);
                emitter().emitNewLine();
            }


            emitter().emit("return 0;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");


        emitter().close();
    }


    default void openStreams(PortDecl port, String name) {
        emitter().emit("std::ifstream %s_file(\"fifo-traces/%s/%1$s.txt\");", port.getName(), name);
        emitter().emit("if(%s_file.fail()){", port.getName());
        {
            emitter().increaseIndentation();
            emitter().emit("std::cout <<\"%s input file not found!\" << std::endl;", port.getName());
            emitter().emit("return 1;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void channels(PortDecl port) {
        emitter().emit("hls::stream< %s > %s;", backend().typeseval().type(backend().types().declaredPortType(port)), port.getName());
    }

    default void refQueues(PortDecl port) {
        emitter().emit("std::queue< %s > qref_%s;", backend().typeseval().type(backend().types().declaredPortType(port)), port.getName());
    }

    default void writeToStream(PortDecl port, Connection connection) {
        emitter().emit("std::string %s_line;", port.getName());
        emitter().emit("while(std::getline(%s_file, %1$s_line)){", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("std::istringstream iss(%s_line);", port.getName());
            emitter().emit("%s %s_tmp;", backend().typeseval().type(backend().types().declaredPortType(port)), port.getName());
            emitter().emit("iss >> %s_tmp;", port.getName());
            emitter().emit("%s.write((%s) %s_tmp);", connection != null ? backend().vnetwork().queueNames().get(connection) : port.getName(), backend().typeseval().type(backend().types().declaredPortType(port)), port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void storeToQueueRef(PortDecl port) {
        emitter().emit("std::string %s_line;", port.getName());
        emitter().emit("while(std::getline(%s_file, %1$s_line)){", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("std::istringstream iss(%s_line);", port.getName());
            emitter().emit("%s %s_tmp;", backend().typeseval().type(backend().types().declaredPortType(port)), port.getName());
            emitter().emit("iss >> %s_tmp;", port.getName());
            emitter().emit("qref_%s.push((%s) %1$s_tmp);", port.getName(), backend().typeseval().type(backend().types().declaredPortType(port)));

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void outputCounters(PortDecl port) {
        emitter().emit("uint32_t %s_token_counter = 0;", port.getName());
    }

    default void compareOutput(PortDecl port, Connection connection) {
        emitter().emit("while(!%s.empty() && !qref_%s.empty()) {", connection != null ? backend().vnetwork().queueNames().get(connection) : port.getName(), port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("%s got_value = %s.read();", backend().typeseval().type(backend().types().declaredPortType(port)), connection != null ? backend().vnetwork().queueNames().get(connection) : port.getName());
            emitter().emit("%s ref_value = qref_%s.front();", backend().typeseval().type(backend().types().declaredPortType(port)), port.getName());
            emitter().emit("qref_%s.pop();", port.getName());
            emitter().emit("if(got_value != ref_value) {");
            {
                emitter().increaseIndentation();
                Type type = backend().types().declaredPortType(port);
                boolean isSigned = false;
                boolean isAlgebraic = type instanceof AlgebraicType;
                if (type instanceof IntType) {
                    isSigned = ((IntType) type).isSigned();
                }

                emitter().emit("std::cout << \"Port %s: Error !!! Expected value does not match golden reference, Token Counter: \" << %1$s_token_counter << std::endl;", port.getName());
                String refValue = isAlgebraic ? "ref_value" : isSigned ? "signed(ref_value)" : "unsigned(ref_value)";
                String gotValue = isAlgebraic ? "got_value" : isSigned ? "signed(got_value)" : "unsigned(got_value)";
                emitter().emit("std::cout << \"Expected: \" << %s << std::endl;", refValue);
                emitter().emit("std::cout << \"Got: \"      << %s << std::endl;", gotValue);
                emitter().emitNewLine();
                emitter().emit("return 1;");

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            emitter().emit("%s_token_counter++;", port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void printProduced(PortDecl port) {
        emitter().emit("std::cout <<  \"Port %s : \" << %1$s_token_counter << \" produced.\" << std::endl;", port.getName());
    }

}
