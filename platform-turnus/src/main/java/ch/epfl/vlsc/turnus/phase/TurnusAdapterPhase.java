package ch.epfl.vlsc.turnus.phase;

import ch.epfl.vlsc.turnus.adapter.TurnusModelAdapter;
import org.apache.log4j.BasicConfigurator;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.settings.PathSetting;
import se.lth.cs.tycho.settings.Setting;
import turnus.analysis.profiler.dynamic.DynamicProfiler;
import turnus.analysis.profiler.dynamic.util.ProfiledExecutionDataReader;
import turnus.common.TurnusException;
import turnus.common.configuration.Configuration;
import turnus.common.io.Logger;
import turnus.model.ModelsRegister;
import turnus.model.dataflow.Network;
import turnus.model.versioning.Versioner;
import turnus.model.versioning.impl.GitVersioner;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static turnus.common.TurnusConstants.DEFAULT_VERSIONER;
import static turnus.common.TurnusOptions.*;

public class TurnusAdapterPhase implements Phase {

    public static final Setting<Path> etracezPath = new PathSetting() {
        @Override
        public String getKey() {
            return "etracez-path";
        }

        @Override
        public String getDescription() {
            return "Compressed execution trace json file.";
        }

        @Override
        public Path defaultValue(se.lth.cs.tycho.settings.Configuration configuration) {
            return Paths.get("");
        }
    };

    @Override
    public String getDescription() {
        return "StreamBlocks Turnus Adapter Phase";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Logger.info("===== STREAMBLOCKS-TURNUS DYNAMIC EXECUTION ANALYSIS ====");

        ModelsRegister.init();
        BasicConfigurator.configure();

        Logger.info("Configuring the project");
        Path targetPath = context.getConfiguration().get(Compiler.targetPath);
        File directory = targetPath.toFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        Path etracezPath = context.getConfiguration().get(TurnusAdapterPhase.etracezPath);
        if (!etracezPath.toFile().exists()) {
            Logger.error("Execution trace file does not exist: " + etracezPath.toAbsolutePath().toString());
            return null;
        }

        Configuration configuration = new Configuration();
        configuration.setValue(CAL_PROJECT, task.getIdentifier().getLast().toString());
        configuration.setValue(VERSIONER, DEFAULT_VERSIONER);
        configuration.setValue(OUTPUT_DIRECTORY, targetPath.toFile());
        configuration.setValue(COMPRESS_TRACE, true);
        configuration.setValue(EXPORT_GANTT_CHART, false);
        configuration.setValue(BUFFER_SIZE_DEFAULT, 4096);
        configuration.setValue(EXPORT_TRACE, true);


        Logger.info("* QID: %s", task.getIdentifier().toString());
        Logger.info("* Input etracez path: %s", etracezPath.toAbsolutePath().toString());
        Logger.info("* Target path: %s", targetPath.toAbsolutePath().toString());


        try {
            Versioner versioner = new GitVersioner();
            // -- Model Adapter
            Logger.info("Network and profiler building");
            TurnusModelAdapter modelAdapter = new TurnusModelAdapter(task, versioner);
            Network network = modelAdapter.getNetwork();

            // -- Dynamic profiling
            DynamicProfiler profiler = new DynamicProfiler(network);
            profiler.setConfiguration(configuration);

            Logger.info("Parsing the execution profiling data");
            File jsonFile = etracezPath.toFile();
            ProfiledExecutionDataReader reader = new ProfiledExecutionDataReader(profiler, jsonFile);
            reader.start();
            reader.join();

            Logger.info("===== ANALYSIS DONE ====");
        } catch (TurnusException e) {
            e.printStackTrace();
        }


        return null;
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(etracezPath);
    }


}
