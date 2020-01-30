package com.streamgenomics.phase;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.hls.phase.VivadoHLSBackendPhase;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Setting;

import java.nio.file.Path;
import java.util.List;

public class OpalKellyBackendPhase implements Phase {
    @Override
    public String getName() {
        return "OpalKelly Backend Phase";
    }

    @Override
    public String getDescription() {
        return "StreamBlocks code-generator for OpalKelly FPGAs boards";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(PlatformSettings.scopeLivenessAnalysis, PlatformSettings.runOnNode);
    }

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

    /**
     * Auxiliary Path
     */
    private Path auxiliaryPath;


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

        // -- Auxiliary path
        auxiliaryPath = PathUtils.createDirectory(codeGenPath, "auxiliary");

        // -- RTL testbench path
        PathUtils.createDirectory(codeGenPath, "rtl-tb");

        // -- Source path
        PathUtils.createDirectory(codeGenPath, "src");

        // -- Include path
        PathUtils.createDirectory(codeGenPath, "include");

        // -- Source testbench path
        PathUtils.createDirectory(codeGenPath, "src-tb");

        PathUtils.createDirectory(codeGenPath, "host");

        // -- WCFG path
        PathUtils.createDirectory(codeGenPath, "wcfg");

        // -- XDC path
        PathUtils.createDirectory(codeGenPath, "xdc");

        // -- Projects path
        PathUtils.createDirectory(codeGenPath, "tcl");

        // -- Build path
        PathUtils.createDirectory(targetPath, "build");

        // -- Output
        Path outputPath = PathUtils.createDirectory(targetPath, "output");

        // -- Fifo traces
        PathUtils.createDirectory(outputPath, "fifo-traces");

        // -- Kernel path
        PathUtils.createDirectory(outputPath, "kernel");
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
        VivadoHLSBackend backend = MultiJ.from(VivadoHLSBackend.class).bind("task").to(task).bind("context").to(context)
                .instance();

        // -- Generate instances
        VivadoHLSBackendPhase.generateInstrances(backend);

        // -- Generate Globals
        VivadoHLSBackendPhase.generateGlobals(backend);

        // -- Generate Network
        VivadoHLSBackendPhase.generateNetwork(backend);

        // -- Generate Testbenches
        VivadoHLSBackendPhase.generateTestbenches(backend);

        // -- Generate Wcfg
        VivadoHLSBackendPhase.generateWcfg(backend);

        // -- Generate Auxiliary
        VivadoHLSBackendPhase.generateAuxiliary(backend);

        return null;
    }


}
