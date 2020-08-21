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
public interface InputStageMem {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }
    default void getInputStageMem(PortDecl port) {
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(identifier + "_input_stage_mem.cpp"));
        backend().includeSystem("stdint.h");
        backend().includeSystem("hls_stream.h");
        backend().includeUser("iostage.h");
        emitter().emitNewLine();
        emitter().emit("using namespace iostage;");
        emitter().emitNewLine();
        emitter().emit("uint32_t %s_input_stage_mem(%s) {", port.getName(), entityPorts(port));
        emitter().emit("#pragma HLS INTERFACE m_axi port=%s_size offset=direct bundle=%1$s max_read_burst_length=256 max_write_burst_length=256", port.getName());
        emitter().emit("#pragma HLS INTERFACE m_axi port=%s_buffer offset=direct bundle=%1$s max_read_burst_length=256 max_write_burst_length=256", port.getName());
        emitter().emit("#pragma HLS INTERFACE ap_fifo port=%s", port.getName());
        emitter().emit("#pragma HLS INTERFACE ap_fifo port=%s_offset", port.getName());
        emitter().emit("#pragma HLS INTERFACE ap_ctrl_hs register port=return");
        {
            emitter().increaseIndentation();

            emitter().emit("static class_input_stage_mem< %s > i_%s_input_stage_mem;", backend().declarations().declaration(backend().types().declaredPortType(port), ""), port.getName());
            emitter().emitNewLine();

            emitter().emit("return i_%s_input_stage_mem(%1$s_requested_size, %1$s_size, %1$s_buffer, fifo_count, fifo_size, %1$s, %1$s_offset);", port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().close();
    }

    default String entityPorts (PortDecl port){
        Type type = backend().types().declaredPortType(port);
        List<String> ports = new ArrayList<>();
        ports.add(String.format("uint32_t %s_requested_size", port.getName()));
        ports.add(String.format("bus_t *%s_size", port.getName()));
        ports.add(String.format("bus_t *%s_buffer", port.getName()));
        ports.add("uint32_t fifo_count");
        ports.add("uint32_t fifo_size");
        
        ports.add(backend().declarations().portDeclaration(port));
        ports.add(String.format("hls::stream< uint64_t > &%s_offset", port.getName()));
        return String.join(", ", ports);
    }

}
