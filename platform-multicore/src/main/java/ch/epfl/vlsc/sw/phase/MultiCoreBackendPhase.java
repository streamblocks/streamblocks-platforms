package ch.epfl.vlsc.sw.phase;

import ch.epfl.vlsc.configuration.Configuration;
import ch.epfl.vlsc.configuration.ConfigurationManager;
import ch.epfl.vlsc.platformutils.ControllerToGraphviz;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.backend.MulticoreBackend;
import ch.epfl.vlsc.sw.ir.PartitionLink;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Setting;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;

public class MultiCoreBackendPhase implements Phase {


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
        return "StreamBlocks Multicore code-generator for Pthread enabled platforms.";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(
                PlatformSettings.scopeLivenessAnalysis,
                PlatformSettings.runOnNode,
                PlatformSettings.defaultBufferDepth,
                PlatformSettings.defaultQueueDepth,
                PlatformSettings.enableSystemC);
    }

    /**
     * Create the backend directories
     *
     * @param context
     */
    private void createDirectories(Context context) {

        // -- get the target path
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


        // -- change the target path when partitioning is enabled
        if (context.getConfiguration().isDefined(PlatformSettings.PartitionNetwork) &&
                context.getConfiguration().get(PlatformSettings.PartitionNetwork)) {
            Path oldTargetPath = context.getConfiguration().get(Compiler.targetPath);
            Path newTargetPath = PathUtils.createDirectory(oldTargetPath, "multicore");
            context.getConfiguration().set(Compiler.targetPath, newTargetPath);
        }

        // -- Create Directories
        createDirectories(context);

        // -- Instantiate backend, bind current compilation task and the context
        MulticoreBackend backend = MultiJ.from(MulticoreBackend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();


        // -- Generate Auxiliary
        generateAuxiliary(backend);

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

        // -- Generate Node scripts
        generateNodeScripts(backend);

        // -- Generate configuration
        generateConfiguration(task, context);
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
    public static void generateInstrances(MulticoreBackend multicoreBackend) {
        for (Instance instance : multicoreBackend.task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = multicoreBackend.globalnames().entityDecl(instance.getEntityName(), true);
            Entity entity = entityDecl.getEntity();
            if (!entityDecl.getExternal()) {
                if (entity instanceof PartitionLink) {
                    multicoreBackend.context()
                            .getReporter()
                            .report(new Diagnostic(Diagnostic.Kind.INFO, "Emitting PartitionLink instance"));

                    multicoreBackend.plink().generatePLink(instance);
//                    multicoreBackend.devicehandle().generateDeviceHandle(instance);
                } else
                    multicoreBackend.instance().generateInstance(instance);

            }

        }
    }

    /**
     * Generates the various CMakeLists.txt for building the generated code
     *
     * @param multicoreBackend
     */
    public static void generateCmakeLists(MulticoreBackend multicoreBackend) {
        // -- Project CMakeLists
        multicoreBackend.cmakelists().projectCMakeLists();

        // -- CodeGen CMakeLists
        multicoreBackend.cmakelists().codegenCMakeLists();

        // -- Super project (when partitioning is enabled) CMakeLists
        multicoreBackend.cmakelists().superProjectCMakeListst();
    }

    public static void generateNodeCmakeLists(MulticoreBackend multicoreBackend) {
        // -- Node CodeGen CMakeLists
        multicoreBackend.cmakelists().codegenNodeCCCMakeLists();
    }

    /**
     * Generate Globals
     *
     * @param multicoreBackend
     */
    public static void generateGlobals(MulticoreBackend multicoreBackend) {
        // -- Globals Source
        multicoreBackend.globals().globalSource();

        // -- Globals Header
        multicoreBackend.globals().generateHeader();
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
            String instanceWithQID = instance.getInstanceName();
            GlobalEntityDecl entityDecl = multicoreBackend.globalnames().entityDecl(instance.getEntityName(), true);
            ControllerToGraphviz dot = new ControllerToGraphviz(entityDecl, instanceWithQID, PathUtils.getAuxiliary(multicoreBackend.context()).resolve(instanceWithQID + ".dot"));
            dot.print();
        }
    }

    private void generateConfiguration(CompilationTask task, Context context) {
        Configuration xcf = new Configuration();

        // -- Set Configuration Network
        Configuration.Network xcfNetwork = new Configuration.Network();
        xcfNetwork.setId(task.getIdentifier().toString());
        xcf.setNetwork(xcfNetwork);

        // -- Create a Partition
        Configuration.Partitioning partitioning = new Configuration.Partitioning();
        Configuration.Partitioning.Partition partition = new Configuration.Partitioning.Partition();

        partition.setId((short) 0);
        partition.setPe("x86_64");
        partition.setScheduling("ROUND_ROBIN");
        partition.setCodeGenerator("sw");
        partition.setHost(true);

        // -- Create instances
        for (Instance instance : task.getNetwork().getInstances()) {
            Configuration.Partitioning.Partition.Instance xcfInstance = new Configuration.Partitioning.Partition.Instance();
            xcfInstance.setId(instance.getInstanceName());
            partition.getInstance().add(xcfInstance);
        }

        partitioning.getPartition().add(partition);
        xcf.setPartitioning(partitioning);

        // -- Code generator
        Configuration.CodeGenerators xcfCodeGenerators = new Configuration.CodeGenerators();
        Configuration.CodeGenerators.CodeGenerator xcfSwCodeGenerator = new Configuration.CodeGenerators.CodeGenerator();
        xcfSwCodeGenerator.setId("sw");
        xcfSwCodeGenerator.setPlatform("multicore");
        xcfCodeGenerators.getCodeGenerator().add(xcfSwCodeGenerator);

        xcf.setCodeGenerators(xcfCodeGenerators);

        // -- Connections

        Configuration.Connections xcfConnections = new Configuration.Connections();
        for (Connection connection : task.getNetwork().getConnections()) {
            Configuration.Connections.Connection fifoConnection = new Configuration.Connections.Connection();
            fifoConnection.setSize(4096);
            fifoConnection.setSource(connection.getSource().getInstance().get());
            fifoConnection.setSourcePort(connection.getSource().getPort());
            fifoConnection.setTarget(connection.getTarget().getInstance().get());
            fifoConnection.setTargetPort(connection.getTarget().getPort());
            xcfConnections.getConnection().add(fifoConnection);
        }
        xcf.setConnections(xcfConnections);

        // -- Write XCF File
        try {

            File xcfFile = new File(binPath + File.separator + "configuration.xcf");
            ConfigurationManager.write(xcfFile, xcf);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }


    public static void generateNodeScripts(MulticoreBackend multicoreBackend) {
        multicoreBackend.nodescripts().scriptNetwork();
        multicoreBackend.nodescripts().pythonScriptNode();
    }

    /**
     * Copy the MulticoreBackend resources to the target directory
     *
     * @param multicoreBackend
     */
    private void copyBackendResources(MulticoreBackend multicoreBackend) {

        boolean hasPlink = multicoreBackend.context().getConfiguration().isDefined(PlatformSettings.PartitionNetwork)
                && multicoreBackend.context().getConfiguration().get(PlatformSettings.PartitionNetwork);
        boolean isSimulated = multicoreBackend.context().getConfiguration().isDefined(PlatformSettings.enableSystemC)
                && multicoreBackend.context().getConfiguration().get(PlatformSettings.enableSystemC);
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
            // -- Copy __arrayCopy.h
            Files.copy(getClass().getResourceAsStream("/arraycopy/__arrayCopy.h"), PathUtils.getTargetCodeGenInclude(multicoreBackend.context()).resolve("__arrayCopy.h"), StandardCopyOption.REPLACE_EXISTING);

            // -- Copy streamblcoks.py
            Files.copy(getClass().getResourceAsStream("/python/streamblocks.py"), PathUtils.getTargetBin(multicoreBackend.context()).resolve("streamblocks.py"), StandardCopyOption.REPLACE_EXISTING);


            if (hasPlink) {
                // -- replace some files if plink is available
                Files.copy(getClass().getResourceAsStream("/plink/CMakeLists.txt"), libPath.resolve("CMakeLists.txt"),
                        StandardCopyOption.REPLACE_EXISTING);
                String sourceDirectory = isSimulated ? "/plink/systemc/" : "/plink/opencl/";
                URL plinkUrl = getClass().getResource(sourceDirectory);
                System.out.println(plinkUrl);
                if (plinkUrl.toString().contains("jar")) {
                    PathUtils.copyFromJar(getClass().getResource(sourceDirectory).toURI(), sourceDirectory, libPath);

                } else {
                    Path plinkLibSourcePath = Paths.get(getClass().getResource(sourceDirectory).toURI());
                    PathUtils.copyDirTree(plinkLibSourcePath, libPath,
                            StandardCopyOption.REPLACE_EXISTING);
                }


            }
        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not copy multicoreBackend resources"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (FileSystemNotFoundException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, String.format("Could not copy multicoreBackend resources")));
        }


    }


}
