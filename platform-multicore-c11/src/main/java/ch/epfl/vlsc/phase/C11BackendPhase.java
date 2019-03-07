package ch.epfl.vlsc.phase;

import ch.epfl.vlsc.backend.Controllers;
import ch.epfl.vlsc.backend.MulticoreBackend;
import ch.epfl.vlsc.platformutils.ControllerToGraphviz;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Setting;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;

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

    /**
     * Auxiliary Path
     */
    private Path auxiliaryPath;

    @Override
    public String getDescription() {
        return "StreamBlocks Multicore C11 Platform for Tycho compiler";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(Controllers.scopeLivenessAnalysis);
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

        // -- Auxiliary path
        auxiliaryPath = PathUtils.createDirectory(codeGenPath, "auxiliary");
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
        MulticoreBackend backend = MultiJ.from(MulticoreBackend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();


        // -- Copy MulticoreBackend resources
        copyBackendResources(backend);

        // -- Generate main
        generateMain(backend);

        // -- Generate Instances
        generateInstrances(backend);

        // -- Generate Globals
        generateGlobals(backend);

        // -- Generate CMakeLists
        generateCmakeLists(backend);

        // -- Generate Auxiliary
        generateAuxiliary(backend);
        return task;
    }

    /**
     * Generates main and the initialization of the network
     *
     * @param multicoreBackend
     */
    private void generateMain(MulticoreBackend multicoreBackend) {
        multicoreBackend.main().main();
    }

    /**
     * Generate Instances
     *
     * @param multicoreBackend
     */
    private void generateInstrances(MulticoreBackend multicoreBackend) {
        for (Instance instance : multicoreBackend.task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = multicoreBackend.globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal())
                multicoreBackend.instance().generateInstance(instance);
        }
    }

    /**
     * Generates the various CMakeLists.txt for building the generated code
     *
     * @param multicoreBackend
     */
    private void generateCmakeLists(MulticoreBackend multicoreBackend) {
        // -- Project CMakeLists
        multicoreBackend.cmakelists().projectCMakeLists();

        // -- CodeGen CMakeLists
        multicoreBackend.cmakelists().codegenCMakeLists();
    }

    /**
     * Generate Globals
     *
     * @param multicoreBackend
     */
    private void generateGlobals(MulticoreBackend multicoreBackend) {
        // -- Globals Source
        multicoreBackend.globals().globalSource();

        // -- Globals Header
        multicoreBackend.globals().globalHeader();
    }

    /**
     * Generate Auxiliary files for visualization
     *
     * @param multicoreBackend
     */
    private void generateAuxiliary(MulticoreBackend multicoreBackend) {

        // -- Network to DOT
        multicoreBackend.netoworkToDot().generateNetworkDot();

        // -- Actor Machine Controllers to DOT
        for (Instance instance : multicoreBackend.task().getNetwork().getInstances()) {
            String instanceWithQID = multicoreBackend.instaceQID(instance.getInstanceName(), "_");
            GlobalEntityDecl entityDecl = multicoreBackend.globalnames().entityDecl(instance.getEntityName(), true);
            ControllerToGraphviz dot = new ControllerToGraphviz(entityDecl, instanceWithQID, PathUtils.getAuxiliary(multicoreBackend.context()).resolve(instanceWithQID + ".dot"));
            dot.print();
        }
    }

    /**
     * Copy a path from jar
     *
     * @param source
     * @param target
     * @throws URISyntaxException
     * @throws IOException
     */
    public void copyFromJar(String source, final Path target) throws URISyntaxException, IOException {
        URI resource = getClass().getResource("").toURI();
        FileSystem fileSystem = FileSystems.newFileSystem(
                resource,
                Collections.<String, String>emptyMap()
        );


        final Path jarPath = fileSystem.getPath(source);

        Files.walkFileTree(jarPath, new SimpleFileVisitor<Path>() {

            private Path currentTarget;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                currentTarget = target.resolve(jarPath.relativize(dir).toString());
                Files.createDirectories(currentTarget);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(jarPath.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

        });
    }

    /**
     * Copy the MulticoreBackend resources to the target directory
     *
     * @param multicoreBackend
     */
    private void copyBackendResources(MulticoreBackend multicoreBackend) {

        try {
            // -- Copy Runtime
            URL url = getClass().getResource("/lib/");
            // -- Temporary hack to launch it from command line
            if (url.toString().contains("jar")) {
                copyFromJar("/lib", libPath);
            } else {
                Path libResourcePath = Paths.get(url.toURI());
                PathUtils.copyDirTree(libResourcePath, libPath, StandardCopyOption.REPLACE_EXISTING);
            }
            // -- Copy __arrayCopy.h
            Files.copy(getClass().getResourceAsStream("/arraycopy/__arrayCopy.h"), PathUtils.getTargetCodeGenInclude(multicoreBackend.context()).resolve("__arrayCopy.h"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not copy multicoreBackend resources"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (FileSystemNotFoundException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, String.format("Could not copy multicoreBackend resources")));
        }
    }

}
