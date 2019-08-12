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
public interface OutputStage {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getOutputStage(PortDecl port){
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(identifier + "_output_stage.cpp"));
        backend().includeSystem("stdint.h");
        backend().includeSystem("hls_stream.h");
        backend().includeUser("output_stage.h");
        emitter().emitNewLine();

        emitter().emit("uint32_t %s_output_stage(%s) {", port.getName(), entityPorts(port));
        emitter().emit("#pragma HLS INTERFACE m_axi port=%s_size offset=direct bundle=%1$s", port.getName());
        emitter().emit("#pragma HLS INTERFACE m_axi port=%s_buffer offset=direct bundle=%1$s", port.getName());
        emitter().emit("#pragma HLS INTERFACE ap_ctrl_hs register port=return");
        {
            emitter().increaseIndentation();

            emitter().emit("static class_output_stage< %s > i_%s_output_stage;", backend().declarations().declaration(backend().types().declaredPortType(port), ""), port.getName());
            emitter().emitNewLine();

            emitter().emit("return i_%s_output_stage(%1$s, core_done, %1$s_available_size, %1$s_size, %1$s_buffer);", port.getName());


            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().close();
    }

    default String entityPorts (PortDecl port){
        Type type = backend().types().declaredPortType(port);
        List<String> ports = new ArrayList<>();
        ports.add(backend().declarations().portDeclaration(port));
        ports.add("bool core_done");
        ports.add(String.format("uint32_t %s_available_size", port.getName()));
        ports.add(String.format("uint32_t *%s_size", port.getName()));
        ports.add(String.format("uint64_t *%s_buffer", port.getName()));

        return String.join(", ", ports);
    }
}