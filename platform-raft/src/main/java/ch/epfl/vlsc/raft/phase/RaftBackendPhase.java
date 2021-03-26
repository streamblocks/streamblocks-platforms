package ch.epfl.vlsc.raft.phase;

import ch.epfl.vlsc.platformutils.ControllerToGraphviz;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.raft.backend.RaftBackend;
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
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class RaftBackendPhase implements Phase {
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
     * Libraries path
     */
    private Path libPath;

    /**
     * Auxiliary Path
     */
    private Path auxiliaryPath;

    @Override
    public String getDescription() {
        return "StreamBlocks OpenCL Platform";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(
                PlatformSettings.scopeLivenessAnalysis,
                PlatformSettings.defaultQueueDepth
        );
    }

    /**
     * Create backend directories
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

        // -- Auxiliary path
        auxiliaryPath = PathUtils.createDirectory(codeGenPath, "auxiliary");
    }

    /**
     * Generate Auxiliary files for visualization
     *
     * @param backend
     */
    private void generateAuxiliary(RaftBackend backend) {

        // -- Network to DOT
        Network network = backend.task().getNetwork();
        String fileName = String.join("_", backend.task().getIdentifier().parts());
        Path dotPath = PathUtils.getAuxiliary(backend.context()).resolve(fileName + ".dot");
        backend.networkToDot().generateNetworkDot(network, fileName, dotPath);

        // -- Actor Machine Controllers to DOT
        for (Instance instance : backend.task().getNetwork().getInstances()) {
            String instanceWithQID = instance.getInstanceName();
            GlobalEntityDecl entityDecl = backend.globalnames().entityDecl(instance.getEntityName(), true);
            ControllerToGraphviz dot = new ControllerToGraphviz(entityDecl, instanceWithQID, PathUtils.getAuxiliary(backend.context()).resolve(instanceWithQID + ".dot"));
            dot.print();
        }
    }

    private void generateInstances(RaftBackend backend) {
        for (Instance instance : backend.task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend.globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal())
                backend.instance().emitInstance(instance);
        }
    }

    private void copyResources(RaftBackend backend) {
        try {
            // -- Copy Runtime
            URL url = getClass().getResource("/lib/");
            // -- Temporary hack to launch it from command line
            if (url.toString().contains("jar")) {
                PathUtils.copyFromJar(getClass().getResource("").toURI(), "/lib", libPath);
            } else {
                Path libResourcePath = Paths.get(url.toURI());
                PathUtils.copyDirTree(libResourcePath, libPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not copy OpenCL backend resources"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (FileSystemNotFoundException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, String.format("Could not copy OpenCL backend resources")));
        }
    }

    private void generateGlobals(RaftBackend backend){
        backend.globals().generateHeader();
    }

    private void generateMain(RaftBackend backend){
        backend.main().generateMain();
    }

    private void generateCMakeLists(RaftBackend backend){
        // -- Project CMakeLists.txt
        backend.cmakelists().projectCMakeLists();

        // -- Code generation CMakeLists.txt
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
        RaftBackend backend = MultiJ.from(RaftBackend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();

        // -- Generate Auxiliary
        generateAuxiliary(backend);

        // -- Generate instances
        generateInstances(backend);

        // -- Generate globals
        generateGlobals(backend);

        // -- Generate Main (Network)
        generateMain(backend);

        // --Generate CMakeLists
        generateCMakeLists(backend);

        // -- Copy Resources
        copyResources(backend);

        return task;
    }
}
