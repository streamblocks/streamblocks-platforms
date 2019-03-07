package ch.epfl.vlsc.phase;

import ch.epfl.vlsc.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;
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

        // -- Code Generation paths
        codeGenPath = PathUtils.createDirectory(targetPath, "code-gen");

        // -- RTL path
        rtlPath = PathUtils.createDirectory(codeGenPath, "rtl");

        // -- RTL testbench path
        PathUtils.createDirectory(codeGenPath, "rtl-tb");

        // -- Source path
        PathUtils.createDirectory(codeGenPath, "src");

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

        return task;
    }


    private void generateInstrances(VivadoHLSBackend backend){
        for (Instance instance : backend.task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend.globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()){
                backend.instance().generateInstance(instance);
            }
        }
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
        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not copy multicoreBackend resources"));
        }
    }


}
