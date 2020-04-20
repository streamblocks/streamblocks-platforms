package ch.epfl.vlsc.orcc.phase;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.ControllerToGraphviz;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Setting;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.List;

public class OrccBackendPhase implements Phase {

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

    /**
     * Auxiliary Path
     */
    private Path auxiliaryPath;

    @Override
    public String getDescription() {
        return "StreamBlocks code-generator for RVC decoders using Orcc C runtime.";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(PlatformSettings.scopeLivenessAnalysis, PlatformSettings.defaultBufferDepth, PlatformSettings.enableTraces);
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

        // -- Build path
        buildPath = PathUtils.createDirectory(targetPath, "build");

        // -- Binary path
        binPath = PathUtils.createDirectory(targetPath, "bin");

        // -- Traces path
        if (context.getConfiguration().get(PlatformSettings.enableTraces)) {
            PathUtils.createDirectory(binPath, "traces");
        }

        // -- Auxiliary path
        auxiliaryPath = PathUtils.createDirectory(codeGenPath, "auxiliary");
    }

    /**
     * Generate Auxiliary files for visualization
     *
     * @param backend
     */
    private void generateAuxiliary(OrccBackend backend) {

        // -- Network to DOT
        Network network = backend.task().getNetwork();
        String fileName = String.join("_", backend.task().getIdentifier().parts());
        Path dotPath = PathUtils.getAuxiliary(backend.context()).resolve(fileName + ".dot");
        backend.networkToDot().generateNetworkDot(network, fileName, dotPath);

        // -- Actor Machine Controllers to DOT
        for (Instance instance : backend.task().getNetwork().getInstances()) {
            String instanceWithQID = backend.instaceQID(instance.getInstanceName(), "_");
            GlobalEntityDecl entityDecl = backend.globalnames().entityDecl(instance.getEntityName(), true);
            ControllerToGraphviz dot = new ControllerToGraphviz(entityDecl, instanceWithQID, PathUtils.getAuxiliary(backend.context()).resolve(instanceWithQID + ".dot"));
            dot.print();
        }
    }

    /**
     * Copy the MulticoreBackend resources to the target directory
     *
     * @param backend the code generation backend
     */
    private void copyBackendResources(OrccBackend backend) {

        try {
            // -- Copy Runtime
            URL url = getClass().getResource("/runtime/");
            // -- Temporary hack to launch it from command line
            if (url.toString().contains("jar")) {
                PathUtils.copyFromJar(getClass().getResource("").toURI(), "/libs", targetPath);
            } else {
                Path libResourcePath = Paths.get(url.toURI());
                PathUtils.copyDirTree(libResourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            // -- Copy __arrayCopy.h
            Files.copy(getClass().getResourceAsStream("/runtime/README.txt"), PathUtils.getTarget(backend.context()).resolve("README.txt"), StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not copy platform resources"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (FileSystemNotFoundException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, String.format("Could not copy platform resources")));
        }
    }

    /**
     * Generate Instances
     *
     * @param backend
     */
    public static void generateInstrances(OrccBackend backend) {
        for (Instance instance : backend.task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend.globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal())
                backend.instance().generateInstance(instance);
        }
    }

    /**
     * Generates main and the initialization of the network
     *
     * @param backend
     */
    private void generateMain(OrccBackend backend) {
        backend.main().getTop();
    }


    /**
     * Generate Globals
     *
     * @param backend
     */
    public static void generateGlobals(OrccBackend backend) {
        // -- Globals Source
        backend.globals().globalSource();

        // -- Globals Header
        backend.globals().globalHeader();
    }

    /**
     * Generates the various CMakeLists.txt for building the generated code
     *
     * @param backend
     */
    public static void generateCmakeLists(OrccBackend backend) {
        // -- Project CMakeLists
        backend.cmakelists().projectCMakeLists();

        // -- CodeGen CMakeLists
        backend.cmakelists().codegenCMakeLists();
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
        OrccBackend backend = MultiJ.from(OrccBackend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();

        // -- Generate Auxiliary
        generateAuxiliary(backend);

        // -- Copy MulticoreBackend resources
        copyBackendResources(backend);

        // -- Generate Main
        generateMain(backend);

        // -- Generate Instances
        generateInstrances(backend);

        // -- Generate Globals
        generateGlobals(backend);

        // -- Generate CMakeLists
        generateCmakeLists(backend);

        return task;
    }

}
