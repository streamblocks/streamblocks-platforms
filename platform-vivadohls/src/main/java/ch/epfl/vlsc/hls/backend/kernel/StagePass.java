package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.List;

@Module
public interface StagePass {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getStagePass(PortDecl port){
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(identifier + "_stage_pass.cpp"));
        backend().includeSystem("stdint.h");
        backend().includeSystem("hls_stream.h");
        backend().includeUser("stage_pass.h");
        emitter().emitNewLine();

        emitter().emit("void %s_stage_pass(%s) {", port.getName(), entityPorts(port));
        emitter().emit("#pragma HLS INTERFACE ap_ctrl_none port=return name=return");
        {
            emitter().increaseIndentation();

            emitter().emit("static class_stage_pass< %s > i_%s_stage_pass;", backend().declarations().declaration(backend().types().declaredPortType(port), ""), port.getName());
            emitter().emitNewLine();

            emitter().emit("return i_%s_stage_pass(STREAM_IN, STREAM_OUT);", port.getName());

            emitter().decreaseIndentation();
        }


        emitter().emit("}");
        emitter().close();
    }

    default String entityPorts (PortDecl port){
        Type type = backend().types().declaredPortType(port);
        List<String> ports = new ArrayList<>();
        ports.add(String.format("hls::stream< %s > &STREAM_IN", backend().declarations().declaration(backend().types().declaredPortType(port), "")));
        ports.add(String.format("hls::stream< %s > &STREAM_OUT", backend().declarations().declaration(backend().types().declaredPortType(port), "")));
        return String.join(", ", ports);
    }

}
