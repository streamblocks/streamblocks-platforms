package ch.epfl.vlsc.mlir.phase;

import ch.epfl.vlsc.mlir.backend.MlirBackend;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.backend.MulticoreBackend;
import ch.epfl.vlsc.sw.phase.MultiCoreBackendPhase;
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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.List;

public class MlirPhase implements Phase {

    /**
     * Code generation path
     */
    private Path codeGenPath;

    /**
     * Target Path
     */
    private Path targetPath;


    @Override
    public String getDescription() {
        return "StreamBlocks MLIR code-generator for making use of the MLIR/CIRCT toolchain for HW/SW generation.";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(PlatformSettings.scopeLivenessAnalysis, PlatformSettings.runOnNode);
    }


    /**
     * Create the backend directories
     *
     * @param context
     */
    private void createDirectories(Context context) {
        // -- Get target Path
        targetPath = context.getConfiguration().get(Compiler.targetPath);
        // -- Code Generation paths
        codeGenPath = PathUtils.createDirectory(targetPath, "code-gen");
    }

    /**
     * Generates main and the initialization of the network
     *
     * @param mlirBackend
     */
    private void generateMain(MlirBackend mlirBackend) {
        mlirBackend.main().main();
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
        MlirBackend mlirBackend = MultiJ.from(MlirBackend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();

        generateMain(mlirBackend);

        // -- Set the multicore platform to run on Node
        context.getConfiguration().set(PlatformSettings.runOnNode, true);

        return task;
    }

}
