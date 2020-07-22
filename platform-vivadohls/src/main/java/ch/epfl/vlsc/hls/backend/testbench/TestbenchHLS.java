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
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    default void generateInstanceTestbench(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add entity to box
        backend().entitybox().set(entity);

        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSrcTb(backend().context()).resolve(instance.getInstanceName() + "_tb.cpp");
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
            entity.getInputPorts().forEach(this::openStreams);

            // -- Output Streams
            entity.getOutputPorts().forEach(this::openStreams);

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
                entity.getInputPorts().forEach(this::writeToStream);
            }

            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// -- Store output tokens to the reference queue");
                entity.getInputPorts().forEach(this::storeToQueueRef);
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

                emitter().emit("IO io;");
                emitter().emitNewLine();

                for (PortDecl port : entity.getInputPorts()) {
                    emitter().emit("io.%s_count = %1$s.size();", port.getName());
                    emitter().emit("io.%s_peek = %1$s._data[0];", port.getName());
                }
                for (PortDecl port : entity.getOutputPorts()) {
                    emitter().emit("io.%s_count = 0;", port.getName());
                    emitter().emit("io.%s_size = 512", port.getName());
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
                    entity.getOutputPorts().forEach(this::compareOutput);
                }

                emitter().emit("end_of_execution = ret == RETURN_WAIT;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            if(!entity.getOutputPorts().isEmpty()){
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

    default void openStreams(PortDecl port) {
        Instance instance = backend().instancebox().get();
        emitter().emit("std::ifstream %s_file(\"fifo-traces/%s/%1$s.txt\");", port.getName(), instance.getInstanceName());
        emitter().emit("if(%s_file.fail()){", port.getSafeName());
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

    default void writeToStream(PortDecl port) {
        emitter().emit("std::string %s_line;", port.getName());
        emitter().emit("while(std::getline(%s_file, %1$s_line)){", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("std::istringstream iss(%s_line);", port.getName());
            emitter().emit("int %s_tmp;", port.getName());
            emitter().emit("iss >> %s_tmp;", port.getName());
            emitter().emit("%s.write((%s) %1$s_tmp);", port.getName(), backend().typeseval().type(backend().types().declaredPortType(port)));

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
            emitter().emit("int %s_tmp;", port.getName());
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

    default void compareOutput(PortDecl port) {
        emitter().emit("if(!%s.empty() && !qref_%1$s.empty()) {", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("%s got_value = %s.read();", backend().typeseval().type(backend().types().declaredPortType(port)), port.getName());
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

    default void printProduced(PortDecl port){
        emitter().emit("std::cout <<  \"Port %s : \" << %1$s_token_counter << \" produced.\" << std::endl;", port.getName());
    }

}
