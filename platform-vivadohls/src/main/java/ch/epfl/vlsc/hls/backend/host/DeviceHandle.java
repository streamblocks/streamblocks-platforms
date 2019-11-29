package ch.epfl.vlsc.hls.backend.host;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import ch.epfl.vlsc.settings.PlatformSettings;

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
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @file DeviceHandle.java
 * @brief DeviceHandle code generator
 * @author: Mahyar Emami (Mahyar.Emami@epfl.ch)
 * 
 */
@Module
public interface DeviceHandle {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Boolean C99() {

        return backend().context().getConfiguration().get(PlatformSettings.C99Host);
    }

    default Emitter emitter() {
        return backend().emitter();
    }

    default String defaultCLEvent() {
        return "cl_event";
    }

    default String defaultCLMem() {
        return "cl_mem";
    }

    default String defaultIntType() {
        return "uint32_t";
    }

    default String defaultSizeType() {
        return "size_t";
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

    default String getC99PreFix() {
        return C99() ? "DeviceHandle_" : "";
    }

    default String getDevClassPointerType() {
        return C99() ? "DeviceHandle_t*" : "";
    }

    default String getDevClassPointer() {
        return C99() ? "dev" : "";
    }

    default String getDevClassPointerWithDot() {
        return getDevClassPointer() + getDotOp();
    }

    default String getDevClassPointerWithType() {
        return getDevClassPointerType() + (C99() ? " " : "") + getDevClassPointer();
    }

    default String getDotOp() {
        return C99() ? "->" : "";
    }

    default void OCL_MSG(String format, Object... values) {

        emitter().emit("OCL_MSG(\"%s\");", String.format(format, values));
    }

    default void OCL_ERR(String format, Object... values) {
        emitter().emit("OCL_ERR(\"%s\");", String.format(format, values));
    }

    default void OCL_CHECK(String format, Object... values) {
        emitter().emit("OCL_CHECK(");
        {
            emitter().increaseIndentation();
            emitter().emit("%s", String.format(format, values));
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    default String typeString(PortDecl port) {
        Type type = backend().types().declaredPortType(port);
        String typeStr = backend().declarations().typeseval().type(type).toString();
        return typeStr;
    }

    default void generateDeviceHandle() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Device Handle header

        if (C99()) {
            emitter().open(PathUtils.getTargetCodeGenHost(backend().context()).resolve("device_handle.h"));
        } else {
            emitter().open(PathUtils.getTargetCodeGenHost(backend().context()).resolve("device_handle.hpp"));
        }

        emitter().emit("#ifndef DEVICE_HANDLE_H", identifier.toUpperCase());
        emitter().emit("#define DEVICE_HANDLE_H", identifier.toUpperCase());

        // -- header files
        getIncludes();

        // -- defines

        getDefines();

        // -- OCL MACROS
        getOCLMacros();

        // -- Get util structs
        getUtilStructs();

        // -- DeviceHandle class
        getDeviceHandleClass(network);

        emitter().emit("#endif // DEVICE_HANDLE_H", identifier.toUpperCase());
        emitter().close();

        // -- Device Handle source
        if (C99())
            emitter().open(PathUtils.getTargetCodeGenHost(backend().context()).resolve("device_handle.c"));
        else
            emitter().open(PathUtils.getTargetCodeGenHost(backend().context()).resolve("device_handle.cpp"));

        BufferedReader reader;
        // -- get common methods
        try {
            if (C99())
                reader = new BufferedReader(
                        new InputStreamReader(getClass().getResourceAsStream("/lib/host/device_handle.c")));

            else
                reader = new BufferedReader(
                        new InputStreamReader(getClass().getResourceAsStream("/lib/host/device_handle.cpp")));

            String line = reader.readLine();
            while (line != null) {
                emitter().emitRawLine(line);
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // -- Function definitions
        getCreateCLBuffers();
        getAllocateBuffers();
        getSetArgs();
        getEnqueueWriteBuffer();
        getEnqueueReadBuffer();

        getReleaseMemObjets();
        getSetAndGetPtrs(network.getInputPorts());
        getSetAndGetPtrs(network.getOutputPorts());

        emitter().emit("%s %sis_pending(%s) { return %spending_status;}", defaultIntType(),
                getC99PreFix() + (!C99() ? "DeviceHandle::" : ""), getDevClassPointerWithType(),
                getDevClassPointerWithDot());
        emitter().close();

        // -- Host template
        if (C99())
            emitter().open(PathUtils.getTargetCodeGenHost(backend().context()).resolve("Host.c"));
        else
            emitter().open(PathUtils.getTargetCodeGenHost(backend().context()).resolve("Host.cpp"));

        if (C99())
            emitter().emit("#include \"device_handle.h\"");
        else
            emitter().emit("#include \"device_handle.hpp\"");
        emitter().emitNewLine();
        emitter().emitNewLine();
        emitter().emitNewLine();
        emitter().emitNewLine();
        emitter().emit("int main (int argc, char **argv) {");
        emitter().increaseIndentation();
        {
            getHostMain();
        }
        emitter().decreaseIndentation();
        emitter().emit("}");

        emitter().close();

    }

    default void getIncludes() {
        List<String> includesListC = Arrays
                .asList(new String[] { "CL/cl_ext.h", "CL/opencl.h", "assert.h", "fcntl.h", "math.h", "stdbool.h",
                        "stdio.h", "stdlib.h", "string.h", "sys/stat.h", "sys/time.h", "sys/types.h", "unistd.h" });
        List<String> includeListCpp = Arrays.asList(
                new String[] { "string", "iostream", "algorithm", "array", "chrono", "cstdio", "random", "vector" });

        for (String include : includesListC) {
            emitter().emit("#include <%s>", include);
        }
        if (!C99()) {
            for (String include : includeListCpp) {
                emitter().emit("#include <%s>", include);
            }
        }

    }

    default void getDefines() {
        Network network = backend().task().getNetwork();
        emitter().emitNewLine();
        emitter().emit("// -- OpenCL and actor specific defines");
        emitter().emit("#define OCL_VERBOSE\t\t\t1");
        emitter().emit("#define OCL_ERROR\t\t\t1");
        emitter().emit("#define %s\t\t\t4096", memAlignmentDefine());
        emitter().emit("#define %s\t\t\t8192", bufferSizeDefine());
        emitter().emit("#define %s\t\t\t%d", numInputsDefine(), network.getInputPorts().size());
        emitter().emit("#define %s\t\t\t%s", numOutputsDefine(), network.getOutputPorts().size());
        emitter().emitNewLine();
    }

    default void getOCLMacros() {
        // -- OCL_CHECK
        emitter().emit("// -- helper macros");
        emitter().emitNewLine();
        emitter().emit("#define OCL_CHECK(call)\t\t\\");
        emitter().increaseIndentation();
        {
            emitter().emit("do {\t\t\\");
            emitter().increaseIndentation();
            {
                emitter().emit("cl_int err = call;\t\t\\");
                emitter().emit("if (err != CL_SUCCESS) { \t\t\\");
                emitter().increaseIndentation();
                {
                    emitter().emit("fprintf(stderr, \"Error calling\" #call \", error code is: %%d\", err);\t\t\\");
                    emitter().emit("exit(EXIT_FAILURE);\t\t\\");
                    emitter().emit("}\t\t\\");
                }
                emitter().decreaseIndentation();
                emitter().emit("} while (0);");
            }
            emitter().decreaseIndentation();
        }
        emitter().decreaseIndentation();
        emitter().emitNewLine();
        getOCLMSG("OCL_MSG", "OCL_VERBOSE", "stdout");
        emitter().emitNewLine();
        getOCLMSG("OCL_ERR", "OCL_ERROR", "stderr");
        emitter().emitNewLine();
    }

    default void getOCLMSG(String name, String predicate, String stream) {
        // -- OC_MSG
        emitter().emit("#define %s(fmt, args...)\t\t\\", name);
        emitter().increaseIndentation();
        {
            emitter().emit("do {\t\t\\");
            emitter().increaseIndentation();
            {
                emitter().emit("if (%s)\t\t\\", predicate);
                emitter().emit("\tfprintf(%s, \"OCL_MSG:%%s():%%d: \" fmt, __func__, __LINE__, ##args);   \\", stream);
                emitter().emit("} while (0);");
            }
            emitter().decreaseIndentation();
        }
        emitter().decreaseIndentation();
    }

    default void getUtilStructs() {

        emitter().emit("// -- helper structs");
        emitter().emitNewLine();
        emitter().emit("// -- event information");
        emitter().emit("%sstruct eventInfo {", C99() ? "typedef " : "");
        emitter().increaseIndentation();
        {
            emitter().emit("%s counter;", defaultSizeType());
            emitter().emit("char msg[64];");

        }
        emitter().decreaseIndentation();
        emitter().emit("} %s;", C99() ? "eventInfo" : "");
        emitter().emitNewLine();
        emitter().emit("// -- opencl world struct");
        emitter().emit("%sstruct OCLWorld {", C99() ? "typedef " : "");
        emitter().increaseIndentation();
        {
            emitter().emit("cl_context context;");
            emitter().emit("cl_platform_id platform_id;");
            emitter().emit("cl_device_id device_id;");
            emitter().emit("cl_command_queue command_queue;");

        }
        emitter().decreaseIndentation();
        emitter().emit("} %s;", C99() ? "OCLWorld" : "");
        emitter().emitNewLine();

    }

    default void getDeviceHandleClass(Network network) {
        String identifier = backend().task().getIdentifier().getLast().toString();
        if (!C99()) {
            emitter().emit("class DeviceHandle{");
            emitter().emit("public:");
            {
                emitter().increaseIndentation();

                emitter().emit(
                        "DeviceHandle (char *kernel_name, char *target_device_name, char *dir, bool hw_emu = false);");

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
        } else {
            emitter().emit("typedef struct DeviceHandle_t{");
            {
                emitter().increaseIndentation();
                getClassMembers();
                emitter().decreaseIndentation();

            }
            emitter().emit("} DeviceHandle_t;");
            emitter().emitNewLine();
            emitter().emit("// -- function declrations");
            getMethodsDeclration();
        }

    }

    default void getConstructor() {
        emitter().emit("DeviceHandle%s (char *kernel_name, char *target_device_name, char *dir) {",
                backend().task().getIdentifier().getLast().toString());
        emitter().increaseIndentation();
        {
            emitter().emit("c_int\terr;");
            emitter().emit("buffer_size = %s;", bufferSizeDefine());
            emitter().emit("num_inputs = %s;", numInputsDefine());
            emitter().emit("num_outputs = %s", numOutputsDefine());
            emitter().emit("mem_alignment = %s", memAlignmentDefine());
            emitter().emit("world = xcl_world_singe();");
            emitter().emit("program = xcl_import_binary(world, kernel_name);");
            emitter().emit("clReleaseCommandQueue(world.command_queue);");
            emitter().emit(
                    "world.command_queue = clCreateCommandQueue(world.context, world.device_id, CL_QUEUE_PROFILING_ENABLE | CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, &err);");
            emitter().emit("kernel = xcl_get_kernel(program, kernel_name);");
            OCL_MSG("Kernel_loaded\\n");
            emitter().emit("global = 1;");
            emitter().emit("local = 1;");
            emitter().emit("pending_status = false;");
            OCL_MSG("Allocating buffer\\n");
            emitter().emit("allocate_buffers();");
            emitter().emit("initEvents();");
        }
        emitter().decreaseIndentation();
        emitter().emit("}");

    }

    default void getMethodsDeclration() {

        if (C99()) {
            emitter().emit(
                    "void %s_constructor(%s, char *kernel_name, char *target_device_name, char *dir, bool hw_emu);",
                    getC99PreFix(), getDevClassPointerWithType());
        }
        emitter().emit("// -- General methods");
        emitter().emit("cl_int load_file_to_memory(const char *filename, char **result);");
        emitter().emit("void %srun(%s);", getC99PreFix(), getDevClassPointerWithType());
        emitter().emit("%s %sis_pending(%s);", defaultIntType(), getC99PreFix(), getDevClassPointerWithType());
        emitter().emit("void %sterminate(%s);", getC99PreFix(), getDevClassPointerWithType());
        emitter().emit("void %sallocateBuffers(%s);", getC99PreFix(), getDevClassPointerWithType());
        emitter().emit("void %screateCLBuffers(%s%ssize_t sz);", getC99PreFix(), getDevClassPointerWithType(),
                C99() ? ", " : "");
        emitter().emit("void %ssetArgs(%s);", getC99PreFix(), getDevClassPointerWithType());

        emitter().emit("void %senqueueExecution(%s);", getC99PreFix(), getDevClassPointerWithType());

        emitter().emit("void %senqueueWriteBuffer(%s);", getC99PreFix(), getDevClassPointerWithType());
        emitter().emit("void %senqueueReadBuffer(%s);", getC99PreFix(), getDevClassPointerWithType());
        emitter().emit("void %swaitForDevice(%s);", getC99PreFix(), getDevClassPointerWithType());
        emitter().emit("void %sinitEvents(%s);", getC99PreFix(), getDevClassPointerWithType());
        emitter().emit("void %ssetRequestSize(%s%s%s *req_sz);", getC99PreFix(), getDevClassPointerWithType(),
                C99() ? ", " : "", defaultIntType());
        emitter().emit("void %sreleaseMemObjects(%s);", getC99PreFix(), getDevClassPointerType());
        emitter().emit("void %sreleaseReadEvents(%s);", getC99PreFix(), getDevClassPointerType());
        emitter().emit("void %sreleaseKernelEvent(%s);", getC99PreFix(), getDevClassPointerType());
        emitter().emit("void %sreleaseWriteEvents(%s);", getC99PreFix(), getDevClassPointerType());
        emitter().emit("void %ssetKernelCommand(%s%suint64_t cmd);", getC99PreFix(), getDevClassPointerWithType(),
                C99() ? ", " : "");
        emitter().emit("// -- specific methods");
        emitter().emitNewLine();

        Network network = backend().task().getNetwork();
        emitter().emitNewLine();
        getSetAndGetDecl(network.getInputPorts());
        getSetAndGetDecl(network.getOutputPorts());

    }

    default void getSetAndGetDecl(List<PortDecl> ports) {
        for (PortDecl port : ports) {
            emitter().emit("%s* %sget_%s_buffer_ptr(%s);", typeString(port), getC99PreFix(), port.getName(),
                    getDevClassPointerWithType());
            emitter().emit("%s* %sget_%s_size_ptr(%s);", defaultIntType(), getC99PreFix(), port.getName(),
                    getDevClassPointerWithType());
            emitter().emit("void %sset_%s_buffer_ptr(%s%s%s *ptr);", getC99PreFix(), port.getName(),
                    getDevClassPointerWithType(), C99() ? ", " : "", typeString(port));
            emitter().emit("void %sset_%s_size_ptr(%s%s%s *ptr);", getC99PreFix(), port.getName(),
                    getDevClassPointerWithType(), C99() ? ", " : "", defaultIntType());
        }
    }

    default void getSetAndGetPtrs(List<PortDecl> ports) {
        for (PortDecl port : ports) {
            emitter().emit("%s* %sget_%s_buffer_ptr(%s) { return %s%s_buffer; }", typeString(port),
                    getC99PreFix() + (!C99() ? "DeviceHandle::" : ""), port.getName(), getDevClassPointerWithType(),
                    getDevClassPointerWithDot(), port.getName());
            emitter().emit("%s* %sget_%s_size_ptr(%s) { return %s%s_size; }", defaultIntType(),
                    getC99PreFix() + (!C99() ? "DeviceHandle::" : ""), port.getName(), getDevClassPointerWithType(),
                    getDevClassPointerWithDot(), port.getName());
            emitter().emit("void %sset_%s_buffer_ptr(%s%s%s *ptr) { %s%s_buffer = ptr; }",
                    getC99PreFix() + (!C99() ? "DeviceHandle::" : ""), port.getName(), getDevClassPointerWithType(),
                    C99() ? ", " : "", typeString(port), getDevClassPointerWithDot(), port.getName());
            emitter().emit("void %sset_%s_size_ptr(%s%s%s *ptr) { %s%s_size = ptr; }",
                    getC99PreFix() + (!C99() ? "DeviceHandle::" : ""), port.getName(), getDevClassPointerWithType(),
                    C99() ? ", " : "", defaultIntType(), getDevClassPointerWithDot(), port.getName());
        }
    }

    default void getDestructor() {
        // not implemented
    }

    default void getClassMembers() {

        Network network = backend().task().getNetwork();

        // -- opencl world
        emitter().emit("// -- opencl world");
        emitter().emit("OCLWorld\tworld;");
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

        // -- kernel command
        emitter().emit("//-- kernel command word");
        emitter().emit("uint64_t\tkernel_command;");
        emitter().emit("%s\tcommand_is_set;", defaultIntType());

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
        getEventsDeclration();

        // -- pending status;
        emitter().emit("// -- pending status");
        emitter().emit("%s pending_status;", defaultIntType());

        emitter().emitNewLine();
        emitter().emit("%s global, local;", defaultSizeType());

    }

    default void getHostBufferDeclration(List<PortDecl> ports, String sizeStr) {

        for (PortDecl port : ports) {

            String typeStr = typeString(port);
            emitter().emit("%s *%s_buffer;", typeStr, port.getName());
            emitter().emit("%s *%s_size;", defaultIntType(), port.getName());

        }
    }

    default void getDeviceBufferDeclration(List<PortDecl> ports) {

        for (PortDecl port : ports) {
            emitter().emit("%s\t%s_cl_size;", defaultCLMem(), port.getName());
            emitter().emit("%s\t%s_cl_buffer;", defaultCLMem(), port.getName());
        }
    }

    default void getEventsDeclration() {

        // -- event
        emitter().emit("// -- events ");
        emitter().emit("%s\t%s[%s];", defaultCLEvent(), defaultWriteEvents(), numInputsDefine());
        emitter().emit("%s\t%s[%s + 2 * %s];", defaultCLEvent(), defaultReadEvents(), numInputsDefine(),
                numOutputsDefine());
        emitter().emit("%s\t%s;", defaultCLEvent(), defaultKernelEvent());

        // -- event info
        emitter().emit("// -- events info");
        emitter().emit("eventInfo *read_events_info;");
        emitter().emit("eventInfo *write_events_info;");
        emitter().emit("eventInfo kernel_events_info;");

    }

    default void getCreateCLBuffers() {

        Network network = backend().task().getNetwork();
        if (C99()) {
            emitter().emit("void %screateCLBuffers(%s, size_t sz) {", getC99PreFix(), getDevClassPointerWithType());
        } else {
            emitter().emit("void DeviceHandle::createCLBuffers(size_t sz) {");
        }

        emitter().increaseIndentation();
        {
            emitter().emit("%sbuffer_size = sz;", getDevClassPointerWithDot());
            OCL_MSG("Creating input CL buffers\\n");
            emitter().emit("// -- inputs");
            for (PortDecl port : network.getInputPorts()) {
                String typeStr = typeString(port);
                emitter().emit(
                        "%s%s_cl_buffer = clCreateBuffer(%1$sworld.context, CL_MEM_READ_ONLY, %1$sbuffer_size * sizeof(%s), NULL, NULL);",
                        getDevClassPointerWithDot(), port.getName(), typeStr);
                emitter().emit(
                        "%s%s_cl_size = clCreateBuffer(%1$sworld.context, CL_MEM_WRITE_ONLY, sizeof(%s), NULL, NULL);",
                        getDevClassPointerWithDot(), port.getName(), defaultIntType());
            }
            emitter().emitNewLine();
            OCL_MSG("Creating output CL buffers\\n");
            emitter().emit("// -- outputs");
            for (PortDecl port : network.getOutputPorts()) {
                String typeStr = typeString(port);
                emitter().emit(
                        "%s%s_cl_buffer = clCreateBuffer(%1$sworld.context, CL_MEM_WRITE_ONLY, %1$sbuffer_size * sizeof(%s), NULL, NULL);",
                        getDevClassPointerWithDot(), port.getName(), typeStr);
                emitter().emit(
                        "%s%s_cl_size = clCreateBuffer(%1$sworld.context, CL_MEM_WRITE_ONLY, sizeof(%s), NULL, NULL);",
                        getDevClassPointerWithDot(), port.getName(), defaultIntType());
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void getAllocateBuffers() {
        Network network = backend().task().getNetwork();
        if (C99())
            emitter().emit("void %sallocateBuffers(%s) {", getC99PreFix(), getDevClassPointerWithType());
        else
            emitter().emit("void DeviceHandle::allocateBuffers() {");
        {
            emitter().increaseIndentation();

            for (PortDecl port : network.getInputPorts()) {
                String typeStr = typeString(port);
                emitter().emit("%s%s_buffer = (%s *) aligned_alloc(%1$smem_alignment, sizeof(%s) * %1$sbuffer_size);",
                        getDevClassPointerWithDot(), port.getName(), typeStr, typeStr);
                emitter().emit("%s%s_size = (%s *) aligned_alloc(%1$smem_alignment, sizeof(%s));",
                        getDevClassPointerWithDot(), port.getName(), defaultIntType(), defaultIntType());
            }

            for (PortDecl port : network.getOutputPorts()) {
                String typeStr = typeString(port);
                emitter().emit("%s%s_buffer = (%s *) aligned_alloc(%1$smem_alignment, sizeof(%s) * %1$sbuffer_size);",
                        getDevClassPointerWithDot(), port.getName(), typeStr, typeStr);
                emitter().emit("%s%s_size = (%s *) aligned_alloc(%1$smem_alignment, sizeof(%s));",
                        getDevClassPointerWithDot(), port.getName(), defaultIntType(), defaultIntType());
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("}");

    }

    default void getSetArgs() {
        Network network = backend().task().getNetwork();
        if (C99())
            emitter().emit("void %ssetArgs(%s) {", getC99PreFix(), getDevClassPointerWithType());
        else
            emitter().emit("void DeviceHandle::setArgs() {");
        {
            emitter().increaseIndentation();
            OCL_MSG("Setting kernel args\\n");

            int kernelIndex = 0;
            for (int i = 0; i < network.getInputPorts().size(); i++) {
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclSetKernelArg(%skernel, %d, sizeof(cl_uint), &%1$srequest_size[%d]));",
                        getDevClassPointerWithDot(), kernelIndex, kernelIndex);
                kernelIndex++;
            }

            for (int i = 0; i < network.getOutputPorts().size(); i++) {
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclSetKernelArg(%skernel, %d, sizeof(cl_uint), &%1$sbuffer_size));",
                        getDevClassPointerWithDot(), kernelIndex);
                kernelIndex++;
            }

            for (PortDecl port : network.getInputPorts()) {
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclSetKernelArg(%skernel, %d, sizeof(cl_mem), &%1$s%s_cl_size));",
                        getDevClassPointerWithDot(), kernelIndex, port.getName());
                kernelIndex++;
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclSetKernelArg(%skernel, %d, sizeof(cl_mem), &%1$s%s_cl_buffer));",
                        getDevClassPointerWithDot(), kernelIndex, port.getName());
                kernelIndex++;
            }

            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclSetKernelArg(%skernel, %d, sizeof(cl_mem), &%1$s%s_cl_size));",
                        getDevClassPointerWithDot(), kernelIndex, port.getName());
                kernelIndex++;
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclSetKernelArg(%skernel, %d, sizeof(cl_mem), &%1$s%s_cl_buffer));",
                        getDevClassPointerWithDot(), kernelIndex, port.getName());
                kernelIndex++;

            }
            emitter().emit("OCL_CHECK(");
            emitter().emit("\tclSetKernelArg(%skernel, %d, sizeof(cl_ulong), &%1$skernel_command));",
                    getDevClassPointerWithDot(), kernelIndex);
            kernelIndex++;
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getEnqueueWriteBuffer() {

        Network network = backend().task().getNetwork();
        if (C99())
            emitter().emit("void %senqueueWriteBuffer(%s) {", getC99PreFix(), getDevClassPointerWithType());
        else
            emitter().emit("void DeviceHandle::enqueueWriteBuffer() {");

        emitter().increaseIndentation();
        {
            OCL_MSG("Equeue write buffer\\n");
            int eventIndex = 0;
            emitter().emitNewLine();

            // -- If write buffer is empty, send only a single token
            emitter().emit("size_t req_sz = 1;");

            for (PortDecl port : network.getInputPorts()) {
                OCL_MSG("Enqueue %s\\n", port.getName());
                String typeStr = typeString(port);

                // -- Check the requets size
                emitter().emit("if (%srequest_size[%d] > 0) {", getDevClassPointerWithDot(), eventIndex);
                {
                    emitter().increaseIndentation();

                    emitter().emit("req_sz = %srequest_size[%d];", getDevClassPointerWithDot(), eventIndex);

                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().emit("else {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("//-- send a single token when request_size[] = 0");
                    emitter().emit("req_sz = 1;");
                    OCL_MSG("info: enqueueing an empty buffer\\n");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");

                OCL_CHECK(
                        "clEnqueueWriteBuffer(%sworld.command_queue, %1$s%s_cl_buffer, CL_TRUE, 0, req_sz * sizeof(%s), %1$s%2$s_buffer, 0, NULL, &%1$s%s[%d])",
                        getDevClassPointerWithDot(), port.getName(), typeStr, defaultWriteEvents(), eventIndex);

                emitter().emit("on_completion(%s%s[%d], &%1$swrite_events_info[%3$d]);", getDevClassPointerWithDot(),
                        defaultWriteEvents(), eventIndex);
                eventIndex++;
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void getEnqueueReadBuffer() {

        if (C99())
            emitter().emit("void %senqueueReadBuffer(%s) {", getC99PreFix(), getDevClassPointerWithType());
        else
            emitter().emit("void DeviceHandle::enqueueReadBuffer() {");
        {
            emitter().increaseIndentation();
            Network network = backend().task().getNetwork();
            OCL_MSG("Enqueue read buffer\\n");

            int eventIndex = 0;
            for (PortDecl port : network.getInputPorts()) {

                OCL_MSG("Enqueue read for %s_size\\n", port.getName());
                OCL_CHECK(
                        "clEnqueueReadBuffer(%sworld.command_queue, %1$s%s_cl_size, CL_TRUE, 0, sizeof(%s), %1$s%2$s_size, 1, &%1$s%s, &%1$s%s[%d])",
                        getDevClassPointerWithDot(), port.getName(), defaultIntType(), defaultKernelEvent(),
                        defaultReadEvents(), eventIndex);
                emitter().emit("on_completion(%s%s[%d], &%1$sread_events_info[%3$d]);", getDevClassPointerWithDot(),
                        defaultReadEvents(), eventIndex);
                eventIndex += 1;
            }

            for (PortDecl port : network.getOutputPorts()) {
                OCL_MSG("Enqueue read for %s_size\\n", port.getName());

                OCL_CHECK(
                        "clEnqueueReadBuffer(%sworld.command_queue, %1$s%s_cl_size, CL_TRUE, 0, sizeof(%s), %1$s%2$s_size, 1, &%1$s%s, &%1$s%s[%d])",
                        getDevClassPointerWithDot(), port.getName(), defaultIntType(), defaultKernelEvent(),
                        defaultReadEvents(), eventIndex);
                emitter().emit("on_completion(%s%s[%d], &%1$sread_events_info[%3$d]);", getDevClassPointerWithDot(),
                        defaultReadEvents(), eventIndex);
                eventIndex += 1;

            }

            for (PortDecl port : network.getOutputPorts()) {
                OCL_MSG("Enqueue read for %s_buffer\\n", port.getName());

                OCL_CHECK(
                        "clEnqueueReadBuffer(%sworld.command_queue, %1$s%s_cl_buffer, CL_TRUE, 0, sizeof(%s) * %1$sbuffer_size, %1$s%2$s_buffer, 1, &%1$s%s, &%1$s%s[%d])",
                        getDevClassPointerWithDot(), port.getName(), typeString(port), defaultKernelEvent(),
                        defaultReadEvents(), eventIndex);
                emitter().emit("on_completion(%s%s[%d], &%1$sread_events_info[%3$d]);", getDevClassPointerWithDot(),
                        defaultReadEvents(), eventIndex);
                eventIndex += 1;
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

    }

    default void getEnqueueMigrateToDevice() {

        Network network = backend().task().getNetwork();

        emitter().emit("void DeviceHandle::enqueueMigrateToDevice() {");
        emitter().increaseIndentation();
        {
            OCL_MSG("Enqueueing migration to device\\n");
            int eventIndex = 0;
            for (PortDecl port : network.getInputPorts()) {
                OCL_MSG("Enqueueing %s\\n", port.getName());
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclEnqueueMigrateMemObjects(world.command_queue, 1,");
                emitter().emit("\t\t&%s_cl_buffer, 0, 0, NULL,", port.getName());
                emitter().emit("\t\t&%s[%d]));", defaultWriteEvents(), eventIndex);
                emitter().emitNewLine();
                emitter().emit("on_completion(%s[%d], &write_events_info[%d]);", defaultWriteEvents(), eventIndex,
                        eventIndex);
                eventIndex++;
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void getEnqueueMigrateToHost() {
        Network network = backend().task().getNetwork();
        emitter().emit("void DeviceHandle::enqueueMigrateToHost() {");
        emitter().increaseIndentation();
        {
            OCL_MSG("Enqueueing migration to host\\n");
            int eventIndex = 0;
            for (PortDecl port : network.getInputPorts()) {
                OCL_MSG("Enqueueing %s_size\\n", port.getName());
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclEnqueueMigrateMemObjects(world.command_queue, 1,");
                emitter().emit("\t\t&%s_cl_size, CL_MIGRATE_MEM_OBJECT_HOST, 1,", port.getName());
                emitter().emit("\t\t&%s, &%s[%d]));", defaultKernelEvent(), defaultReadEvents(), eventIndex);

                emitter().emit("on_completion(%s[%d], &read_events_info[%d]);", defaultReadEvents(), eventIndex,
                        eventIndex);
                emitter().emitNewLine();
                eventIndex++;
            }
            for (PortDecl port : network.getOutputPorts()) {
                OCL_MSG("Enqueueing %s_size\\n", port.getName());
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclEnqueueMigrateMemObjects(world.command_queue, 1,");
                emitter().emit("\t\t&%s_cl_size, CL_MIGRATE_MEM_OBJECT_HOST, 1,", port.getName());
                emitter().emit("\t\t&%s, &%s[%d]));", defaultKernelEvent(), defaultReadEvents(), eventIndex);

                emitter().emit("on_completion(%s[%d], &read_events_info[%d]);", defaultReadEvents(), eventIndex,
                        eventIndex);
                emitter().emitNewLine();
                eventIndex++;
            }
            for (PortDecl port : network.getOutputPorts()) {
                OCL_MSG("Enqueueing %s_buffer\\n", port.getName());
                emitter().emit("OCL_CHECK(");
                emitter().emit("\tclEnqueueMigrateMemObjects(world.command_queue, 1,");
                emitter().emit("\t\t&%s_cl_buffer, CL_MIGRATE_MEM_OBJECT_HOST, 1,", port.getName());
                emitter().emit("\t\t&%s, &%s[%d]));", defaultKernelEvent(), defaultReadEvents(), eventIndex);

                eventIndex++;
            }
            emitter().emitNewLine();
            emitter().emit("releaseWriteEvents();");
        }
        emitter().decreaseIndentation();
        emitter().emit("}");

    }

    default void getReleaseMemObjets() {

        if (C99())
            emitter().emit("void %sreleaseMemObjects(%s) {", getC99PreFix(), getDevClassPointerWithType());
        else
            emitter().emit("void DeviceHandle::releaseMemObjects() {");

        Network network = backend().task().getNetwork();

        emitter().increaseIndentation();
        {
            OCL_MSG("Releasing mem objects\\n");
            getReleasePorts(network.getInputPorts());
            getReleasePorts(network.getOutputPorts());
            OCL_MSG("Mem objects released\\n");

        }
        emitter().decreaseIndentation();
        emitter().emit("}");

    }

    default void getReleasePorts(List<PortDecl> ports) {

        for (PortDecl port : ports) {
            emitter().emit("OCL_CHECK(clReleaseMemObject(%s%s_cl_buffer));", getDevClassPointerWithDot(),
                    port.getName());
            emitter().emit("OCL_CHECK(clReleaseMemObject(%s%s_cl_size));", getDevClassPointerWithDot(), port.getName());
        }
    }

    default void getHostMain() {
        String identifier = backend().task().getIdentifier().getLast().toString();
        Network network = backend().task().getNetwork();

        // -- Construct the device handle
        emitter().emit("// -- Construct the device handle");
        if (C99()) {
            emitter().emit("DeviceHandle_t dev;");
            emitter().emit("DeviceHandle_constructor(&dev, \"%s_kernel\", \"%s\", \"%s\");", identifier,
                    "xilinx_kcu1500_dynamic_5_0", "xclbin");
        } else {
            emitter().emit("DeviceHandle dev(\"%s_kernel\", \"%s\", \"%s\");", identifier, "xilinx_kcu1500_dynamic_5_0",
                    "xclbin");
        }
        emitter().emitNewLine();
        emitter().emit("// -- An array holding the request size for all inputs");
        emitter().emit("%s request_size[%d];", defaultIntType(), network.getInputPorts().size());

    }
}
