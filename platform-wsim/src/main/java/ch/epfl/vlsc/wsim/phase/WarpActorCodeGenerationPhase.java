package ch.epfl.vlsc.wsim.phase;

import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.wsim.backend.WSimBackend;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.settings.Setting;

import java.nio.file.Path;
import java.util.List;

public class WarpActorCodeGenerationPhase implements Phase {


    @Override
    public String getDescription() {
        return "Generate C++ Warp Simulation actor code";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(
                PlatformSettings.scopeLivenessAnalysis,
                PlatformSettings.defaultQueueDepth
        );
    }


    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        WSimBackend backend = MultiJ.from(WSimBackend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();
        Path targetPath = PathUtils.getTarget(context);

        Path headerPath = PathUtils.createDirectory(targetPath, "include");
        Path sourcePath = PathUtils.createDirectory(targetPath, "src");

        backend.globals().generateHeader(headerPath);
        for (Instance instance : task.getNetwork().getInstances()) {

            backend.instance().emitInstance(instance, sourcePath, headerPath);
        }

        backend.networkbuilder().buildNetwork(task.getNetwork(), sourcePath);


        return task;
    }
}
