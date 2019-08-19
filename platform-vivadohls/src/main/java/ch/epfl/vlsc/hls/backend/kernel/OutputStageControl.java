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
public interface OutputStageControl {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getOutputStageControl(PortDecl port){
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(identifier + "_output_stage_control.cpp"));
        backend().includeSystem("stdint.h");
        backend().includeSystem("hls_stream.h");
        backend().includeUser("output_stage_control.h");
        emitter().emitNewLine();

        emitter().emit("uint32_t %s_output_stage_control(%s) {", port.getName(), entityPorts(port));
        emitter().emit("#pragma HLS INTERFACE ap_ctrl_hs register port=return");
        {
            emitter().increaseIndentation();

            emitter().emit("static class_output_stage_control< %s > i_%s_output_stage_control;", backend().declarations().declaration(backend().types().declaredPortType(port), ""), port.getName());
            emitter().emitNewLine();

            emitter().emit("return i_%s_output_stage_control(STREAM_IN, STREAM_OUT, core_done);", port.getName());


            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().close();
    }

    default String entityPorts (PortDecl port){
        Type type = backend().types().declaredPortType(port);
        List<String> ports = new ArrayList<>();
        ports.add(String.format("hls::stream< %s > &STREAM_IN",  backend().declarations().declaration(backend().types().declaredPortType(port), "")));
        ports.add(String.format("hls::stream< %s > &STREAM_OUT",  backend().declarations().declaration(backend().types().declaredPortType(port), "")));
        ports.add("bool core_done");

        return String.join(", ", ports);
    }


}
