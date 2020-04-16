package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.PathUtils;



import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.util.ImmutableList;

import se.lth.cs.tycho.reporting.CompilationException;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.sw.ir.PartitionLink;
import org.multij.Module;
import org.multij.Binding;
import org.multij.BindingKind;

import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.reporting.Diagnostic;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author Mahyar Emami (mahayr.emami@epfl.ch)
 * Generates the openCL code neccessary for CPU <-> FPGA communication, used by the PartitionLink instance.
 */
@Module
public interface DeviceHandle {
    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    default void unimpl() {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,"device handle " +
                stackTrace[2].getMethodName() + " method not implemented!"));
    }

    default Emitter emitter() { return backend().emitter(); }
    default String defaultClEvent() { return "cl_event"; }
    default String ClMem() { return "cl_mem"; }
    default String defaultIntType() { return "uint32_t"; }
    default String defaultSizeType() { return "size_t"; }
    default String memAlignment() { return "MEM_ALIGNMENT"; }

    default String defaultWriteEvents() {
        return "write_events";
    }

    default String defaultReadEvents() {
        return "read_events";
    }

    default String defaultKernelEvent() {
        return "kernel_event";
    }

    default String methodPrefix() { return "DeviceHandle_"; }

    default void OclErr(String format, Object... values) {
        emitter().emit("OCL_ERR(\"%s\");", String.format(format, values));
    }

    default void OclCheck(String format, Object... values) {
        emitter().emit("OCL_CHECK(");
        {
            emitter().increaseIndentation();
            emitter().emit("%s", String.format(format, values));
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    default void OclMsg(String format, Object... values) {
        emitter().emit("OCL_MSG(\"%s\");", String.format(format, values));
    }

    default String getMethodName(String method) {
        return methodPrefix() + method;
    }
    default String portType(PortDecl port) {
        return backend().typeseval().type(backend().types().declaredPortType(port));
    }

    default void generateDeviceHandle(Instance instance) {

        String networkId = backend().task().getIdentifier().getLast().toString();

        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        // -- sanity/assertion check
        if (!(entityDecl.getEntity() instanceof PartitionLink))
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Expected an instance of PartitionLink when" +
                            " generating device handle code for the network " + networkId + " but did not find one." +
                            " Make sure the NetworkPartitionPhase and ExtractSWPartitionPhases are turned on."));

        Entity entity = entityDecl.getEntity();

        generateDeviceHandleSource(entity);
        generateDeviceHandleHeader(entity);

    }

    /**
     * Generates the source file device-handle.c
     * @param entity the PartitionLink entity for which the source file is generated.
     */
    default void generateDeviceHandleSource(Entity entity) {
        // -- open output file
        Path artPlinkPath = PathUtils.createDirectory(PathUtils.getTargetLib(backend().context()), "art-plink");
        emitter().open(artPlinkPath.resolve("device-handle.c"));

        BufferedReader reader;
        // -- read the common methods from plink/device-handle.c in resources
        try {
            reader = new BufferedReader(
                    new InputStreamReader(getClass().getResourceAsStream("/lib/art-plink/device-handle.c")));
            String line = reader.readLine();
            while (line != null) {
                emitter().emitRawLine(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not read the resource file " +
                            "lib/art-plink/device-handle.c"));
        }


        // -- methods definitions

        getCreateClBuffers(entity);
        getSetArgs(entity);
        getEnqueueWriteBuffers(entity);
        getEnqueueReadBuffers(entity);

        getReleaseMemObjects(entity);

        getSetAndGetPtrs(Stream.concat(
                entity.getInputPorts().stream(), entity.getOutputPorts().stream()).collect(Collectors.toList()));

        emitter().close();
    }

    default void getCreateClBuffers(Entity entity) {
        emitter().emit("void %s(DeviceHandle_t *dev, %s sz) {",
                getMethodName("createCLBuffers"), defaultSizeType());
        {
            emitter().increaseIndentation();
            emitter().emit("dev->buffer_size = sz;");
            OclMsg("Creating input CL buffers\\n");
            emitter().emitNewLine();
            emitter().emit("// -- input buffers");
            for (PortDecl port: entity.getInputPorts()) {
                String size = String.format("dev->buffer_size * sizeof(%s)", portType(port));
                getCreateCLBuffer(port.getName() + "_cl_buffer", "CL_MEM_READ_ONLY", size);

            }
            emitter().emitNewLine();
            emitter().emit("// -- output buffer");
            for (PortDecl port: entity.getOutputPorts()) {
                String size = String.format("dev->buffer_size * sizeof(%s)", portType(port));
                getCreateCLBuffer(port.getName() + "_cl_buffer", "CL_MEM_WRITE_ONLY", size);
            }
            emitter().emitNewLine();
            emitter().emit("// -- consumed and produces size of streams");
            Stream.concat(entity.getInputPorts().stream(), entity.getOutputPorts().stream()).forEach(
                    p ->
                            getCreateCLBuffer(p.getName() + "_cl_size",
                                    "CL_MEM_WRITE_ONLY", "sizeof(" + defaultIntType() + ")")
            );

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getCreateCLBuffer(String name, String mode, String size) {
        emitter().emit("dev->%s = clCreateCLBuffer(dev->world.context, %s, %s, NULL, NULL);", name, mode, size);
    }
    default void getSetArgs(Entity entity) {
        emitter().emit("void %s(DeviceHandle_t *dev) {", getMethodName("setKernelArgs"));
        {
            emitter().increaseIndentation();
            OclMsg("Setting kernel args\\n");
            ImmutableList<PortDecl> ports =
                    ImmutableList.from(
                            Stream.concat(
                                    entity.getInputPorts().stream(),
                                    entity.getOutputPorts().stream())
                                    .collect(Collectors.toList()));
            int kernelIndex = 0;
            for (PortDecl port: entity.getInputPorts()) {
                emitter().emit("// -- request size for %s", port.getName());
                getSetKernelArg(
                        kernelIndex ++,
                        "sizeof(cl_uint)", port.getName() + "_request_size");
            }
            for (PortDecl port: entity.getOutputPorts()) {
                emitter().emit("// -- device available size for outputs, " +
                        "should be at most as big as buffer_size");
                getSetKernelArg(kernelIndex ++, "sizeof(cl_uint)", "buffer_size");
            }

            for (PortDecl port: ports) {
                emitter().emit("// -- stream size buffer for %s", port.getName());
                getSetKernelArg(kernelIndex++, "sizeof(cl_mem)", port.getName() + "_cl_size");
                emitter().emit("// -- device buffer object for %s", port.getName());
                getSetKernelArg(kernelIndex++, "sizeof(cl_mem)", port.getName() + "_cl_buffer");
            }

            emitter().emit("// -- kernel command arg");
            getSetKernelArg(kernelIndex++, "sizeof(cl_ulong)", "kernel_command");
            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getSetKernelArg(int index, String size, String name) {
        OclCheck("clSetKernelArg(dev->kernel, %d, %s, &dev->%s)", index, size, name);
    }

    default void getEnqueueWriteBuffers(Entity entity) {

        emitter().emit("void %s(DeviceHandle_t *dev) {", getMethodName("enqueueWriteBuffers"));
        {
            emitter().increaseIndentation();


            emitter().emit("size_t req_sz = 1;");
            emitter().emitNewLine();
            for (PortDecl port: entity.getInputPorts()) {
                emitter().emit("if (dev->%s_request_size > 0) { // -- make sure there is something to send",
                        port.getName(), entity.getInputPorts().indexOf(port));
                {
                    emitter().increaseIndentation();
                    emitter().emit("// -- DMA write transfer for %s", port.getName());
                    OclMsg("Enqueueing write for %s", port.getName());
                    emitter().emit("OCL_CHECK(");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("clEnqueueWriteBuffer(");
                        {
                            emitter().increaseIndentation();
                            emitter().emit("dev->world.command_queue, // -- the command queue");
                            emitter().emit("dev->%s_cl_buffer, // -- the cl device buffer", port.getName());
                            emitter().emit("CL_TRUE, // -- blocking write operation"); // TODO: should become CL_FALSE
                            emitter().emit("0, // -- buffer offset, not use");
                            emitter().emit("dev->%s_request_size * sizeof(%s), // -- size of data transfer in byte",
                                    port.getName(), entity.getInputPorts().indexOf(port), portType(port));
                            emitter().emit("dev->%s_buffer, // -- pointer to the host memory", port.getName());
                            emitter().emit("0, // -- number of events in wait list, writes do not wait for anything");
                            emitter().emit("NULL, // -- the event wait list, not used for writes");
                            emitter().emit("&dev->write_buffer_event[%d])); // -- the generated event",
                                    entity.getInputPorts().indexOf(port));
                            emitter().emitNewLine();
                            emitter().emit("// -- register call back for the write event");
                            emitter().emit("on_completion(dev->write_buffer_event[%d], " +
                                    "&dev->write_buffer_event_info[%1$d]);", entity.getInputPorts().indexOf(port));
                            emitter().decreaseIndentation();
                        }
                        emitter().decreaseIndentation();
                    }
                    emitter().decreaseIndentation();
                }
                emitter().emit("} else { // -- else do not make the DMA transfer");
                {
                    emitter().increaseIndentation();
                    OclMsg("Info: skipping a write transfer of size 0 for %s.\\n", port.getName());
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().emitNewLine();
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getEnqueueReadBuffers(Entity entity) {

        emitter().emit("void %s(DeviceHandle_t *dev) {", getMethodName("enqueueReadBuffers"));
        {
            emitter().increaseIndentation();

            emitter().emitNewLine();
            ImmutableList<PortDecl> ports =
                    ImmutableList.from(
                            Stream.concat(
                                    entity.getInputPorts().stream(),
                                    entity.getOutputPorts().stream())
                                    .collect(Collectors.toList()));
            // -- stream size buffers
            emitter().emit("// -- Enqueue read for i/o size buffers");
            for(PortDecl port: ports) {
                OclMsg("Enqueue read for %s\\n", port.getName());
                emitter().emit("OCL_CHECK(");
                {
                    emitter().increaseIndentation();
                    emitter().emit("clEnqueueReadBuffer(");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("dev->world.command_queue, // -- command queue");
                        emitter().emit("dev->%s_cl_size, // -- device buffer", port.getName());
                        emitter().emit("CL_TRUE, //-- blocking read"); // this should remain blocking
                        emitter().emit("0, // -- offset");
                        emitter().emit("sizeof(%s), // -- size of the read transfer in bytes", defaultIntType());
                        emitter().emit("dev->%s_size, // -- host buffer for the stream size", port.getName());
                        emitter().emit("1, // -- number of events to wait on");
                        emitter().emit("&dev->kernel_event, // -- the list of events to wait on");
                        emitter().emit("&dev->read_size_event[%d])); // -- the generated event",
                                ports.indexOf(port));
                        emitter().emitNewLine();
                        emitter().emit("// -- register event call back ");
                        emitter().emit("on_completion(dev->read_size_event[%d], &dev->read_size_event_info[%1$d]);",
                                ports.indexOf(port));
                        emitter().emitNewLine();
                        emitter().decreaseIndentation();
                    }
                    emitter().decreaseIndentation();
                }

            }
            emitter().emitNewLine();
            // -- output buffers
            emitter().emit("// -- Enqueue read for output buffers");
            for(PortDecl port: entity.getOutputPorts()) {
                OclMsg("Enqueue readf for %s\\n", port.getName());

                emitter().emit("if (dev->%s_size[0] > 0) {// -- only read if something was produced",
                        port.getName());
                {
                    emitter().increaseIndentation();
                    emitter().emit("OCL_CHECK(");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("clEnqueueReadBuffer(");
                        {
                            emitter().increaseIndentation();
                            emitter().emit("dev->world.command_queue, // -- command queue");
                            emitter().emit("dev->%s_cl_buffer, // -- device buffer", port.getName());
                            emitter().emit("CL_TRUE, //-- blocking read"); // TODO: Should become CL_FALSE
                            emitter().emit("0, // -- offset");
                            emitter().emit("dev->%s_size[0] * sizeof(%s), // -- size of the read transfer in bytes",
                                    port.getName(), portType(port));
                            emitter().emit("dev->%s_buffer, // -- host buffer for the stream size", port.getName());
                            emitter().emit("1, // -- number of events to wait on");
                            emitter().emit("&dev->kernel_event, // -- the list of events to wait on");
                            emitter().emit("&dev->read_buffer_event[%d])); // -- the generated event",
                                    ports.indexOf(port));
                            emitter().emitNewLine();
                            emitter().emit("// -- register event call back ");
                            emitter().emit("on_completion(dev->read_buffer_event[%d], " +
                                    "&dev->read_buffer_event_info[%1$d]);", ports.indexOf(port));
                            emitter().emitNewLine();
                            emitter().decreaseIndentation();
                        }
                        emitter().decreaseIndentation();
                    }

                    emitter().decreaseIndentation();
                } emitter().emit("} else {");
                {
                    emitter().increaseIndentation();
                    OclMsg("Info: port %s did not produce any data, skipping read transfer.\\n", port.getName());
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

    }
    default void getReleaseMemObjects(Entity entity) {
        emitter().emit("void %s(DeviceHandle_t *dev) {", getMethodName("ReleaseMemObjects"));
        {
            emitter().increaseIndentation();
            Stream.concat(entity.getInputPorts().stream(), entity.getOutputPorts().stream())
                    .forEach(this::getReleaseMemObject);
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getReleaseMemObject(PortDecl port) {
        emitter().emitNewLine();
        OclMsg("Releasing mem object %s_cl_buffer\\n", port.getName());
        OclCheck("clReleaseMemObject(dev->%s_cl_buffer)", port.getName());
        OclMsg("Releasing mem object %s_cl_size\\n", port.getName());
        OclCheck("clReleaseMemObject(dev->%s_cl_size)", port.getName());
        emitter().emitNewLine();
    }

    default void getSetAndGetPtrs(List<PortDecl> ports) {
        emitter().emit("// -- get pointer methods");
        ports.forEach(this::getGetPtr);
        emitter().emitNewLine();
        emitter().emit(" // -- set pointer methods");
        ports.forEach(this::getSetPtr);
    }

    default void getGetPtr(PortDecl port) {

        emitter().emitNewLine();
        emitter().emit("// -- get pointers for %s", port.getName());
        emitter().emit("%s* %s_%s_buffer_ptr(DeviceHandle_t *dev) { return dev->%3$s_buffer; }",
                portType(port), getMethodName("Get"), port.getName());

        emitter().emit("%s* %s_%s_size_ptr(DeviceHandle_t *dev) { return dev->%3$s_size; }",
                defaultIntType(), getMethodName("Get"), port.getName());
        emitter().emitNewLine();
    }

    default void getSetPtr(PortDecl port) {
        emitter().emitNewLine();
        emitter().emit("// -- set pointers for %s", port.getName());
        emitter().emit("void %s_%s_buffer_ptr(DeviceHandle_t *dev, %s *ptr) { dev->%2$s_buffer = ptr; }",
                getMethodName("Set"), port.getName(), portType(port));
        emitter().emit("void %s_%s_size_ptr(DeviceHandle_t *dev, %s *ptr) { dev->%2$s_size = ptr; }",
                getMethodName("Set"), port.getName(), defaultIntType());
        emitter().emitNewLine();
    }

    /**
     * Generates the header file device-handle.h
     * @param entity the PartitionLink entity for which the header file is generated
     */
    default void generateDeviceHandleHeader(Entity entity) {

        // -- open output file
        Path artPlinkPath = PathUtils.createDirectory(PathUtils.getTargetLib(backend().context()), "art-plink");
        emitter().open(artPlinkPath.resolve("device-handle.h"));
        String networkId = backend().task().getIdentifier().getLast().toString();

        emitter().emit("#ifndef DEVICE_HANDLE_H", networkId.toUpperCase());
        emitter().emit("#define DEVICE_HANDLE_H", networkId.toUpperCase());
        // -- header includes
        getIncludes();
        // -- defines
        getDefines(entity);
        // -- OCL macros
        getOclMacros();
        // -- get util structures
        getUtilStructs();
        // -- get device handle struct
        getDeviceHandleStruct(entity);
        emitter().emit("#endif // DEVICE_HANDLE_H", networkId.toUpperCase());
        emitter().close();
    }

    default void getIncludes() {
        ImmutableList<String> includes =
                ImmutableList.of(
                        "CL/cl_ext.h",
                        "CL/opencl.h",
                        "assert.h",
                        "fcntl.h",
                        "math.h",
                        "stdbool.h",
                        "stdio.h",
                        "stdlib.h",
                        "string.h",
                        "sys/stat.h",
                        "sys/time.h",
                        "sys/types.h",
                        "unistd.h");
        includes.forEach(i -> emitter().emit("#include <%s>", i));
        emitter().emitNewLine();
    }

    default void getDefines(Entity entity) {

        emitter().emitNewLine();
        emitter().emit("// -- OpenCL and actor specific defines");
        emitter().emit("#define OCL_VERBOSE\t\t\t1");
        emitter().emit("#define OCL_ERROR\t\t\t1");
        emitter().emit("#define %s\t\t\t4096", memAlignment());
        emitter().emit("#define BUFFER_SIZE (1 << 16)");
        emitter().emit("#define NUM_INPUTS\t\t\t%d", entity.getInputPorts().size());
        emitter().emit("#define NUM_OUTPUTS\t\t\t%s", entity.getOutputPorts().size());
        emitter().emitNewLine();

    }

    default void getOclMacros() {
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
        // -- OCL MSG
        emitter().emit("#define OCL_MSG(fmt, args...)\t\t\\");
        {
            emitter().increaseIndentation();
            emitter().emit("do {\t\t\\");
            {
                emitter().increaseIndentation();
                emitter().emit("if (OCL_VERBOSE)\t\t\\");
                emitter().emit("\tfprintf(stdout, \"OCL_MSG:%%s():%%d: \" fmt, __func__, __LINE__, ##args);\\");
                emitter().emit("} while (0);");
                emitter().decreaseIndentation();
            }
            emitter().decreaseIndentation();
        }
        emitter().emitNewLine();
        // -- OCL ERR
        emitter().emit("#define OCL_MSG(fmt, args...)\t\t\\");
        {
            emitter().increaseIndentation();
            emitter().emit("do {\t\t\\");
            {
                emitter().increaseIndentation();
                emitter().emit("if (OCL_ERROR)\t\t\\");
                emitter().emit("\tfprintf(stderr, \"OCL_ERR:%%s():%%d: \" fmt, __func__, __LINE__, ##args);\\");
                emitter().emit("} while (0);");
                emitter().decreaseIndentation();
            }
            emitter().decreaseIndentation();
        }
    }

    default void getUtilStructs() {

        emitter().emitNewLine();
        emitter().emit("// -- event handling info");
        emitter().emit("typedef struct EventInfo {");
        {
            emitter().increaseIndentation();
            emitter().emit("%s counter;", defaultSizeType());
            emitter().emit("char msg[128];");
            emitter().decreaseIndentation();
        }
        emitter().emit("} EventInfo;");
        emitter().emitNewLine();
        emitter().emit("typedef struct OCLWorld {");
        {
            emitter().increaseIndentation();

            emitter().emit("cl_context context;");
            emitter().emit("cl_platform_id platform_id;");
            emitter().emit("cl_device_id device_id;");
            emitter().emit("cl_command_queue command_queue;");

            emitter().decreaseIndentation();
        }
        emitter().emit("} OCLWorld;");
        emitter().emitNewLine();

    }

    default void getDeviceHandleStruct(Entity entity) {

        emitter().emit("// -- Device Handle struct");
        emitter().emit("typedef struct DeviceHandle_t {");
        {
            emitter().increaseIndentation();
            getDeviceHandleFields(entity);
            emitter().decreaseIndentation();

        }
        emitter().emit("} DeviceHandle_t;");

        emitter().emitNewLine();
        emitter().emit("// -- method declarations");
        getMethodDeclarations(entity);

    }

    class Func {
        public final String name;
        public final String retType;
        public final ImmutableList<Pair<String, String>> args;
        public final boolean global;
        public Func (String retType, String name, List<Pair<String, String>> args, boolean global) {
            this.retType = retType;
            this.name = name;
            this.args = ImmutableList.from(args);
            this.global = global;
        }
        public Func (String retType, String name, List<Pair<String, String>> args) {
            this(retType, name, args, false);
        }
        static public Func of(String retType, String name, List<Pair<String, String>> args, boolean global) {
            return new Func(retType, name, args, global);
        }
        static public Func of(String retType, String name, List<Pair<String, String>> args) {
            return new Func(retType, name, args);
        }
        static public Func of(String retType, String name) {
            return new Func(retType, name, ImmutableList.empty());
        }
    }

    default Pair<String, String> FuncArg(String type, String name) {
        return Pair.of(type, name);
    }

    default void getMethodDeclarations(Entity entity) {

        ImmutableList.Builder<Func> funcs = ImmutableList.builder();

        // -- independent methods
        funcs.addAll(
            Func.of("void", "constructor",
                  ImmutableList.of(
                          FuncArg("char*", "kernel_name"),
                          FuncArg("char*", "target_device_name"),
                          FuncArg("char*", "dir"),
                          FuncArg("bool", "hw_emu"))),
            Func.of("cl_int", "load_file_to_memory",
                  ImmutableList.of(
                          FuncArg("const char*", "filename"),
                          FuncArg("char**", "result")), true),
            Func.of("void", "terminate"),
            Func.of("void", "createCLBuffers"),
            Func.of("void", "setArgs"),
            Func.of("void", "enqueueExecution"),
            Func.of("void", "enqueueWriteBuffer"),
            Func.of("void", "enqueueReadBuffer"),
            Func.of("void", "waitForDevice"),
            Func.of("void", "initEvents"),
            Func.of("void", "releaseMemObjects"),
            Func.of("void", "releaseReadEvents"),
            Func.of("void", "releaseKernelEvent"),
            Func.of("void", "releaseWriteEvent")
        );

        // -- topology dependent methods

        // -- set request size
        entity.getInputPorts().stream().forEachOrdered(
                p -> funcs.add(
                        Func.of("void", "set_" + p.getName() +"_request_size",
                        ImmutableList.of(FuncArg(defaultIntType(), "req_sz")))));
        // -- set pointers
        Stream.concat(entity.getInputPorts().stream(), entity.getOutputPorts().stream()).forEachOrdered(
                p -> funcs.addAll(
                        Func.of("void", "set_" + p.getName() + "_buffer_ptr",
                                ImmutableList.of(FuncArg(portType(p) + "*", "ptr"))),
                        Func.of("void", "set_" + p.getName() + "_size_ptr",
                                ImmutableList.of(FuncArg(defaultIntType() + "*", "ptr")))));
        // -- get pointers
        Stream.concat(entity.getInputPorts().stream(), entity.getOutputPorts().stream()).forEachOrdered(
                p -> funcs.addAll(
                        Func.of(portType(p) + "*", "get_" + p.getName() + "_buffer_ptr"),
                        Func.of(defaultIntType() + "*", "get_" + p.getName() + "_size_ptr")));
        funcs.build().stream().forEachOrdered(this::emitFuncDecl);
    }
    default void emitFuncDecl(Func func) {
        if (func.global) {
            // non-member function
            emitter().emit("%s %s(%s);",
                    func.retType, func.name,
                    String.join(", ",
                            func.args.stream()
                                    .map(arg ->
                                            String.format("%s\t%s", arg._1, arg._2))
                                    .collect(Collectors.toList())));
        } else {
            // member function
            String methodName = "dev" + String.valueOf(func.name.charAt(0)).toUpperCase() + func.name.substring(1);

            ImmutableList<Pair<String, String>> methodArgs = ImmutableList.<Pair<String, String>>builder()
                    .add(FuncArg("DeviceHandle_t*", "dev")).addAll(func.args).build();
            emitter().emit("%s %s(%s);",
                    func.retType, methodName,
                    String.join(", ",
                            methodArgs.stream()
                                    .map(arg ->
                                            String.format("%s\t%s", arg._1, arg._2))
                                    .collect(Collectors.toList())));

        }
    }
    class Pair<X, Y> {
        public final X _1;
        public final Y _2;
        public Pair(X x, Y y) {
            this._1 = x;
            this._2 = y;
        }
        public static <X, Y> Pair<X, Y> of(X x, Y y) {
            return new Pair(x, y);
        }
    }
    class StructField {
        public final Pair<String, String> field;
        public final String description;
        public StructField(Pair<String, String> field) {
            this.field = field;
            description = "";
        }
        public StructField(String type, String name) {
            this.field = new Pair(type, name);
            description = "";
        }

        public StructField(String type, String name, String description) {
            this.field = new Pair(type, name);
            this.description = description;
        }

        public StructField(Pair<String, String> field, String description) {
            this.field = field;
            this.description = description;
        }
        static public StructField of(String type, String name) {
            return new StructField(type, name);
        }
        static public StructField of(String type, String name, String description) {
            return new StructField(type, name, description);
        }
        static public StructField of(Pair<String, String> field) {
            return new StructField(field);
        }
        static public StructField of(Pair<String, String> field, String description) {
            return new StructField(field, description);
        }

    }
    default void getDeviceHandleFields(Entity entity) {

        emitter().emit("// -- openCL world");
        ImmutableList.Builder<StructField> fields = ImmutableList.builder();
        fields.addAll(
                StructField.of("OCLWorld", "world"),
                StructField.of("cl_program", "program"),
                StructField.of("cl_kernel", "kernel"),
                StructField.of(defaultSizeType(), "global"),
                StructField.of(defaultSizeType(), "local"),
                StructField.of(defaultIntType(), "num_inputs"),
                StructField.of(defaultIntType(), "num_outputs"),
                StructField.of(defaultSizeType(), "buffer_size"),
                StructField.of(defaultSizeType(), "mem_alignment"),
                StructField.of("uint64_t", "kernel_command", "the kernel command word (deprecated)"),
                StructField.of(defaultIntType(), "command_is_set", "the kernel command status (deprecated)"),
                StructField.of(defaultIntType(), "pending_status", "status of a kernel run (deprecated)"),
                StructField.of(
                        "cl_event",
                        "write_buffer_event[" + entity.getInputPorts().size() + "]",
                        "an array containing write buffer events"),
                StructField.of(
                        "cl_event",
                        "read_size_event[" + (entity.getInputPorts().size() + entity.getOutputPorts().size()) + "]",
                        "an array containing read size events"),
                StructField.of(
                        "cl_event",
                        "read_buffer_event[" + entity.getOutputPorts().size() + "]",
                        "an array containing read buffer events"),
                StructField.of("cl_event",
                        "kernel_event",
                        "kernel enqueue event"),
                StructField.of("cl_event", "kernel_event", "kernel enqueue event"),
                StructField.of("EventInfo*", "write_buffer_event_info", "write buffer event info"),
                StructField.of("EventInfo*", "read_size_event_info", "read size event info"),
                StructField.of("EventInfo*", "read_buffer_event_info", "read buffer event info"),
                StructField.of("EventInfo ", "kernel_event_info", "kernel enqueue event info")
        );

        for (PortDecl port: entity.getInputPorts()) {
            fields.add(
                    StructField.of(
                            defaultIntType(),
                            port.getName() + "_request_size",
                            "Size of transfer for " + port.getName()));
        }


        Stream.concat(entity.getInputPorts().stream(), entity.getOutputPorts().stream())
                .forEachOrdered(p -> fields.addAll(
                        StructField.of(
                                portType(p) + "*",
                                p.getName() + "_buffer",
                                "host buffer for port " + p.getName()),
                        StructField.of(
                                defaultIntType() + "*",
                                p.getName() + "_size",
                                "host size buffer for port " + p.getName()),
                        StructField.of(
                                "cl_mem",
                                p.getName() + "_cl_buffer",
                                "device buffer for port " + p.getName()
                                ),
                        StructField.of(
                                "cl_mem",
                                p.getName() + "_cl_size",
                                "device size buffer for port " + p.getName())));

        fields.build().forEach(this::emitField);
    }
    default void emitField(StructField field) {
        emitter().emitNewLine();
        if (!field.description.isEmpty())
            emitter().emit("// -- %s", field.description);
        emitter().emit("%s\t\t%s;", field.field._1, field.field._2);
    }

}