package ch.epfl.vlsc.phase;

import ch.epfl.vlsc.backend.Backend;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class C11BackendPhase implements Phase {

    /**
     * Code generation path
     */
    private Path codeGenPath;

    /**
     * Code generation source path
     */
    private Path codeGenPathSrc;

    /**
     * Code generation include path
     */
    private Path codeGenPathInclude;

    /**
     * Libraries path
     */
    private Path libPath;

    /**
     * Binary path
     */
    private Path binPath;

    /**
     * CMake build path
     */
    private Path buildPath;

    /**
     * Target Path
     */
    private Path targetPath;


    @Override
    public String getDescription() {
        return "StreamBlocks Multicore C11 Platform for Tycho compiler";
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
        codeGenPathSrc = PathUtils.createDirectory(codeGenPath, "src");
        codeGenPathInclude = PathUtils.createDirectory(codeGenPath, "include");

        // -- Library paths
        libPath = PathUtils.createDirectory(targetPath, "lib");


        // -- Build path
        buildPath = PathUtils.createDirectory(targetPath, "build");

        // -- Binary path
        binPath = PathUtils.createDirectory(targetPath, "bin");
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        // -- Get Reporter
        Reporter reporter = context.getReporter();
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, getDescription()));
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, "Identifier, " + task.getIdentifier().toString()));
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, "Target Path, " + PathUtils.getTarget(context)));

        // -- Create Context
        createDirectories(context);

        // -- Instantiate backend, bind current compilation task and the context
        Backend backend = MultiJ.from(Backend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();


        // -- Copy Backend resources
        copyBackendResources(backend);

        // -- Generate main
        generateMain(backend);

        // -- Generate CMakeLists
        generateCmakeLists(backend);

        // -- Generate CMakeLists
        return task;
    }

    /**
     * Generates main and the initialization of the network
     *
     * @param backend
     */
    private void generateMain(Backend backend) {
        backend.main().main();
    }

    /**
     * Generates the various CMakeLists.txt for building the generated code
     *
     * @param backend
     */
    private void generateCmakeLists(Backend backend) {
        // -- Project CMakeLists
        backend.cmakelists().projectCMakeLists();
    }


    /**
     * Copy the Backend resources to the target directory
     *
     * @param backend
     */
    private void copyBackendResources(Backend backend) {
        try {
            URL url = getClass().getResource("/lib/");
            Path libResourcePath = Paths.get(url.toURI());
            PathUtils.copyDirTree(libResourcePath, libPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not copy backend resources"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

}
