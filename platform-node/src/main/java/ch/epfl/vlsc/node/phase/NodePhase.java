package ch.epfl.vlsc.node.phase;

import ch.epfl.vlsc.node.backend.NodeBackend;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.phase.C11BackendPhase;
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

public class NodePhase implements Phase {

    /**
     * Code generation path
     */
    private Path codeGenPath;

    /**
     * Code generation path for SW actors
     */
    private Path codeGenPathCC;

    /**
     * Code generation path for HW actors
     */
    private Path codeGenPathHW;

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
        return "StreamBlocks Node code-generator for heterogeneous platforms.";
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
        codeGenPathCC = PathUtils.createDirectory(codeGenPath, "cc");
        codeGenPathHW = PathUtils.createDirectory(codeGenPath, "hls");
        PathUtils.createDirectory(codeGenPathCC, "src");
        PathUtils.createDirectory(codeGenPathCC, "include");
        PathUtils.createDirectory(codeGenPathHW, "src");
        PathUtils.createDirectory(codeGenPathHW, "include");

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

        // -- Create Directories
        createDirectories(context);

        // -- Instantiate backend, bind current compilation task and the context
        NodeBackend nodeBackend = MultiJ.from(NodeBackend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();


        // -- Set the multicore platform to run on Node
        context.getConfiguration().set(PlatformSettings.runOnNode, true);

        // -- Copy MulticoreBackend resources
        copyBackendResources(context);

        // FIXME: make it more general
        // -- Generate globals for SW
        C11BackendPhase.generateGlobals(nodeBackend.multicore());

        // -- Generate instances for SW
        C11BackendPhase.generateInstrances(nodeBackend.multicore());


        // -- Generate CMakeLists for Projects
        generateCmakeLists(nodeBackend);

        // -- Generate CMakeLists for SW instances
        C11BackendPhase.generateNodeCmakeLists(nodeBackend.multicore());

        // -- Generate Node Script
        // FIXME: Make it more general
        C11BackendPhase.generateNodeScripts(nodeBackend.multicore());

        return task;
    }


    private void generateCmakeLists(NodeBackend nodeBackend) {
        nodeBackend.cmakelists().projectCMakeLists();
        nodeBackend.cmakelists().projectCodegenNodeCMakeLists();
    }


    /**
     * Copy the MulticoreBackend resources to the target directory
     *
     * @param context
     */
    private void copyBackendResources(Context context) {
        try {
            // -- Copy Runtime
            URL url = getClass().getResource("/lib/");
            // -- Temporary hack to launch it from command line
            if (url.toString().contains("jar")) {
                PathUtils.copyFromJar(getClass().getResource("").toURI(), "/lib", libPath);
                File pyFile = new File(binPath + File.separator + "streamblocks.py");
                PathUtils.copyFromJar(getClass().getResource("").toURI(), "/python/streamblocks.py", pyFile.toPath());
            } else {
                Path libResourcePath = Paths.get(url.toURI());
                PathUtils.copyDirTree(libResourcePath, libPath, StandardCopyOption.REPLACE_EXISTING);
                // -- Copy streamblcoks.py
                Files.copy(getClass().getResourceAsStream("/python/streamblocks.py"), PathUtils.getTargetBin(context).resolve("streamblocks.py"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not copy multicoreBackend resources"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (FileSystemNotFoundException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, String.format("Could not copy Node platform resources")));
        }
    }


}
