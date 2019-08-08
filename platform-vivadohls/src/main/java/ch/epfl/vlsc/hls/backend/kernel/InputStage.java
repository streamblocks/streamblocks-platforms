package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;

import java.nio.file.Path;

@Module
public interface InputStage {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getInputStage(PortDecl port){
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetScript(backend().context()).resolve(identifier + "_input_stage.cpp"));

        emitter().close();

    }

}
