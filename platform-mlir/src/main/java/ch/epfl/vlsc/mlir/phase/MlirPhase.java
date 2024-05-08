package ch.epfl.vlsc.mlir.phase;

import ch.epfl.vlsc.mlir.backend.MlirBackend;
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

public class MlirPhase implements Phase {

    /**
     * Code generation path
     */
    private Path codeGenPath;

    /**
     * Path for build system files
     */
    private Path buildSystemPath;

    /**
     * Target Path
     */
    private Path targetPath;

    private Path testBenchPath;




    @Override
    public String getDescription() {
        return "StreamBlocks MLIR code-generator for making use of the MLIR/CIRCT toolchain for HW/SW generation.";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(
                PlatformSettings.scopeLivenessAnalysis,
                PlatformSettings.defaultBufferDepth
        );
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
        // -- Build System path
        buildSystemPath = PathUtils.createDirectory(targetPath, "scripts");

        testBenchPath = PathUtils.createDirectory(targetPath, "testbench");

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
        generateBuildScript(mlirBackend);
        generateTestBench(mlirBackend);

        return task;
    }

    private void generateBuildScript(MlirBackend mlirBackend) {
        mlirBackend.buildSystem().generateBuildScript();
    }

    private void generateTestBench(MlirBackend mlirBackend){
        mlirBackend.testBenchGenerator().generateTestBench();
    }

}
