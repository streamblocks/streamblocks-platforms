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
public interface OutputStageMem {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getOutputStageMem(PortDecl port) {
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(identifier + "_output_stage.cpp"));
        backend().includeSystem("stdint.h");
        backend().includeSystem("hls_stream.h");
        backend().includeUser("iostage.h");
        emitter().emitNewLine();
//        emitter().emit("using namespace iostage;");
        emitter().emitNewLine();
        emitter().emit("uint32_t %s_output_stage(%s) {", port.getName(), entityPorts(port));
        emitter().emit("#pragma HLS INTERFACE m_axi port=ocl_buffer.data_buffer offset=direct bundle=ocl_bundle", port.getName());
        emitter().emit("#pragma HLS INTERFACE m_axi port=ocl_buffer.meta_buffer offset=direct bundle=ocl_bundle", port.getName());
        emitter().emit("#pragma HLS INTERFACE ap_fifo port=data_stream");
        emitter().emit("#pragma HLS INTERFACE ap_fifo port=meta_stream");
        emitter().emit("#pragma HLS INTERFACE ap_ctrl_hs register port=return");
        {
            emitter().increaseIndentation();

            emitter().emit("static iostage::OutputMemoryStage< %s > i_%s_output_stage_mem;", backend().declarations().declaration(backend().types().declaredPortType(port), ""), port.getName());
            emitter().emitNewLine();

            emitter().emit("return i_%s_output_stage_mem(ocl_buffer, fifo_count, data_stream, meta_stream);", port.getName());


            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        emitter().close();
    }

    default String entityPorts(PortDecl port) {
        List<String> ports = new ArrayList<>();
        Type type = backend().types().declaredPortType(port);
        String typeStr = backend().typeseval().type(type);

        ports.add(String.format("iostage::CircularBuffer< %s > ocl_buffer", typeStr));
//        ports.add(String.format("bus_t *%s_size", port.getName()));
//        ports.add(String.format("bus_t *%s_buffer", port.getName()));
//
        ports.add("uint32_t fifo_count");
        ports.add(String.format("hls::stream< %s > &data_stream", typeStr));
//        ports.add(backend().declarations().portDeclaration(port));
        ports.add(String.format("hls::stream< bool > &meta_stream", port.getName()));
        return String.join(", ", ports);
    }
}
