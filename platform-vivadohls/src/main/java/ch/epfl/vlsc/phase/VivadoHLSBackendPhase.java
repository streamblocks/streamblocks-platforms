package ch.epfl.vlsc.phase;

import ch.epfl.vlsc.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class VivadoHLSBackendPhase implements Phase {

    /**
     * Target Path
     */
    private Path targetPath;

    /**
     * Code generation path
     */
    private Path codeGenPath;

    /**
     * Code generation path
     */
    private Path rtlPath;

    /**
     * cmake path for finding the HLS tools
     */
    private Path cmakePath;

    /**
     * Script paths for hls generate by cmake
     */
    private Path scriptsPath;

    @Override
    public String getDescription() {
        return "StreamBlocks Vivado HLS Platform for Tycho compiler";
    }


    /**
     * Create the backend directories
     *
     * @param context
     */
    private void createDirectories(Context context) {
        // -- Get target Path
        targetPath = context.getConfiguration().get(Compiler.targetPath);

        // -- Cmake path
        cmakePath = PathUtils.createDirectory(targetPath, "cmake");

        // -- Script paths for cmake
        scriptsPath = PathUtils.createDirectory(targetPath, "scripts");

        // -- Code Generation paths
        codeGenPath = PathUtils.createDirectory(targetPath, "code-gen");

        // -- RTL path
        rtlPath = PathUtils.createDirectory(codeGenPath, "rtl");

        // -- RTL testbench path
        PathUtils.createDirectory(codeGenPath, "rtl-tb");

        // -- Source path
        PathUtils.createDirectory(codeGenPath, "src");

        // -- Include path
        PathUtils.createDirectory(codeGenPath, "include");

        // -- Source testbench path
        PathUtils.createDirectory(codeGenPath, "src-tb");

        // -- WCFG path
        PathUtils.createDirectory(codeGenPath, "wcfg");

        // -- XDC path
        PathUtils.createDirectory(codeGenPath, "xdc");

        // -- Projects path
        PathUtils.createDirectory(codeGenPath, "tcl");

        // -- Build path
        PathUtils.createDirectory(targetPath, "build");

    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        // -- Get Reporter
        Reporter reporter = context.getReporter();
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, getDescription()));
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, "Identifier, " + task.getIdentifier().toString()));
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, "Target Path, " + PathUtils.getTarget(context)));

        // -- Create Directories
        createDirectories(context);

        // -- Instantiate backend, bind current compilation task and the context
        VivadoHLSBackend backend = MultiJ.from(VivadoHLSBackend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();

        // -- Copy Backend resources
        copyBackendResources(backend);

        // -- Generate instances
        generateInstrances(backend);

        // -- Generate Globals
        generateGlobals(backend);

        // -- Generate Network
        generateNetwork(backend);

        // -- Generate Testbenches
        generateTestbenches(backend);

        // -- Generate Project CMakeList
        generateCmakeLists(backend);

        // -- Generate Wcfg
        generateWcfg(backend);

        return task;
    }

    /**
     * Generate Source code for instances
     *
     * @param backend
     */

    private void generateInstrances(VivadoHLSBackend backend) {
        for (Instance instance : backend.task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend.globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                backend.instance().generateInstance(instance);
            }
        }
    }

    /**
     * Generate Verilog network
     *
     * @param backend
     */
    private void generateNetwork(VivadoHLSBackend backend) {
        backend.vnetwork().generateNetwork();
    }

    /**
     * Generate gloabals
     *
     * @param backend
     */
    private void generateGlobals(VivadoHLSBackend backend) {

        // -- Globals Header
        backend.globals().globalHeader();
    }

    /**
     * Generate tesbenches for Network and Instances
     *
     * @param backend
     */
    private void generateTestbenches(VivadoHLSBackend backend) {
        // -- Network
        Network network = backend.task().getNetwork();

        // -- Network Verilog Testbench
        backend.testbench().generateTestbench(network);

        // -- Instance Verilog Testbench
        network.getInstances().forEach(backend.testbench()::generateTestbench);
    }

    private void generateWcfg(VivadoHLSBackend backend){
        // -- Wcfg
        Network network = backend.task().getNetwork();
        backend.wcfg().getWcfg(network);
    }


    /**
     * Generates the various CMakeLists.txt for building the generated code
     *
     * @param backend
     */
    private void generateCmakeLists(VivadoHLSBackend backend) {
        // -- Project CMakeLists
        backend.cmakelists().projectCMakeLists();
    }

    /**
     * Copy the backend resources
     *
     * @param backend
     */
    private void copyBackendResources(VivadoHLSBackend backend) {
        try {
            // -- Vivado HLS Fifo
            Files.copy(getClass().getResourceAsStream("/lib/verilog/fifo.v"), PathUtils.getTargetCodeGenRtl(backend.context()).resolve("fifo.v"), StandardCopyOption.REPLACE_EXISTING);

            // -- Find Vivado hls for cmake
            Files.copy(getClass().getResourceAsStream("/lib/cmake/FindVivadoHLS.cmake"), PathUtils.getTargetCmake(backend.context()).resolve("FindVivadoHLS.cmake"), StandardCopyOption.REPLACE_EXISTING);

            // -- Synthesis script for Vivado HLS as an input to CMake
            Files.copy(getClass().getResourceAsStream("/lib/cmake/Synthesis.tcl.in"), PathUtils.getTargetScripts(backend.context()).resolve("Synthesis.tcl.in"), StandardCopyOption.REPLACE_EXISTING);


        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not copy backend resources"));
        }
    }


}
