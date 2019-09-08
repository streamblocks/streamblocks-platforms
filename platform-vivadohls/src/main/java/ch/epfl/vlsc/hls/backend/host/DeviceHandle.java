package ch.epfl.vlsc.hls.backend.host;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface DeviceHandle {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default String defaultCLEvent() {
        return "cl_event";
    }

    default String defaultCLMem() {
        return "cl_mem";
    }

    default String defaultIntType() {
        return "std::uint32_t";
    }

    default String defaultSizeType() {
        return "std::size_t";
    }

    default String numInputsDefine() {
        return "NUM_INPUTS";
    }

    default String numOutputsDefine() {

        return "NUM_OUTPUTS";
    }

    default String bufferSizeDefine() {

        return "BUFFER_SIZE";
    }

    default String memAlignmentDefine() {

        return "MEM_ALIGNMENT";
    }

    default String defaultWriteEvents() {
        return "write_events";
    }

    default String defaultReadEvents() {
        return "read_events";
    }

    default String defaultKernelEvent() {
        return "kernel_event";
    }

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateDeviceHandle() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // .. Device Handle header
        emitter().open(PathUtils.getTargetCodeGenHost(backend().context()).resolve(identifier + "_device_handle.h"));

        emitter().emit("#ifndef %s_DEVICE_HANDLE_H", identifier.toUpperCase());
        emitter().emit("#define %s_DEVICE_HANDLE_H", identifier.toUpperCase());

        // -- header files
        getIncludes();

        // -- DeviceHandle class
        getDeviceHandleClass(network);

        emitter().emit("#endif // %s_DEVICE_HANDLE_H", identifier.toUpperCase());
        emitter().close();
    }

    default void getIncludes() {
        List<String> includesList = Arrays
                .asList(new String[] { "CL/cl_ext.h", "CL/opencl.h", "xcl.h", "assert.h", "fcntl.h", "iostream",
                        "math.h", "stdbool.h", "stdio.h", "stdlib.h", "string.h", "string", "sys/stat.h", "sys/time.h",
                        "sys/types.h", "unistd.h", "algorithm", "array", "chrono", "cstdio", "random", "vector" });
        for (String include : includesList) {
            emitter().emit("#include <%s>", include);
        }

    }

    default void getDeviceHandleClass(Network network) {
        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emit("class DeviceHandle%s {", identifier.toUpperCase());
        emitter().emit("public:");
        {
            emitter().increaseIndentation();
            getConstructor();

            getMethodsDeclration();

            getDestructor();

            emitter().decreaseIndentation();
        }
        emitter().emit("private:");
        {
            emitter().increaseIndentation();

            getClassMembers();

            emitter().decreaseIndentation();

        }
        emitter().emit("}; // DeviceHandle%s", identifier.toUpperCase());
    }

    default void getConstructor() {

    }

    default void getMethodsDeclration() {

    }

    default void getDestructor() {

    }

    default void getClassMembers() {

        Network network = backend().task().getNetwork();

        // -- opencl world
        emitter().emit("// -- opencl world");
        emitter().emit("xcl_world\tworld;");
        emitter().emit("cl_program\tprogram;");
        emitter().emit("cl_kernel\tkernel;");
        // -- io info
        emitter().emit("// -- io info");
        emitter().emit("%s\tnum_inputs;\t// equals %s", defaultIntType(), network.getInputPorts().size());
        emitter().emit("%s\tnum_outputs;\t// equals %s", defaultIntType(), network.getOutputPorts().size());
        emitter().emit("%s\tbuffer_size;", defaultSizeType());
        emitter().emit("%s\tmem_alignment;", defaultSizeType());

        // -- request size
        emitter().emit("//-- request size buffers", defaultIntType());
        emitter().emit("%s\trequest_size[%s];", defaultIntType(), numInputsDefine());

        // -- input buffers

        emitter().emit("// -- input buffers");
        getHostBufferDeclration(network.getInputPorts(), numInputsDefine());

        // -- output buffers

        emitter().emit("// -- output buffers");
        getHostBufferDeclration(network.getOutputPorts(), numOutputsDefine());

        // -- input cl buffers

        emitter().emit("// -- input cl buffers");
        getDeviceBufferDeclration(network.getInputPorts());

        // -- output cl buffers

        emitter().emit("// -- output cl buffers");
        getDeviceBufferDeclration(network.getOutputPorts());

        // -- events

        emitter().emit("// -- events");
        getEventsDeclration();

    }

    default void getHostBufferDeclration(List<PortDecl> ports, String sizeStr) {

        for (PortDecl port : ports) {
            Type type = backend().types().declaredPortType(port);
            emitter().emit("%s;",
                    backend().declarations().declaration(type, String.format("*%s_size[%s]", port.getName(), sizeStr)));
            emitter().emit("%s;", backend().declarations().declaration(type,
                    String.format("*%s_buffer[%s]", port.getName(), sizeStr)));
        }
    }

    default void getDeviceBufferDeclration(List<PortDecl> ports) {

        for (PortDecl port : ports) {
            emitter().emit("%s\t%s_size;", defaultCLMem(), port.getName());
            emitter().emit("%s\t%s_buffer;", defaultCLMem(), port.getName());
        }
    }

    default void getEventsDeclration() {

        emitter().emit("%s\t%s[%s];", defaultCLEvent(), defaultWriteEvents(), numInputsDefine());
        emitter().emit("%s\t%s[%s + 2 * %s];", defaultCLEvent(), defaultReadEvents(), numInputsDefine(),
                numOutputsDefine());
        emitter().emit("%s\t%s;", defaultCLEvent(), defaultKernelEvent());

    }
}
