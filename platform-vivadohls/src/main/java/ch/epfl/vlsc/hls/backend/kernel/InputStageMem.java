package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.reporting.Diagnostic;
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

        Type type = backend().types().declaredPortType(port);

        int bitWidth = backend().typeseval().sizeOfBits(type);


        String typeStr = backend().typeseval().type(type);

        if (bitWidth % 8 != 0) {
            backend().context().getReporter().report(
                    new Diagnostic(Diagnostic.Kind.WARNING, "AXI port " + port.getName() + " of type " + typeStr + " will be treated as uint8_t.")
            );
            typeStr = "uint8_t";
        }


        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(identifier + "_input_stage.cpp"));
        backend().includeSystem("stdint.h");
        backend().includeSystem("hls_stream.h");
        backend().includeUser("iostage.h");
        emitter().emitNewLine();
//        emitter().emit("using namespace iostage;");
        emitter().emitNewLine();
        emitter().emit("uint32_t %s_input_stage(%s) {", port.getName(), entityPorts(port, typeStr));
        //emitter().emit("#pragma HLS INTERFACE m_axi port=%s_size offset=direct bundle=%1$s max_read_burst_length=256 max_write_burst_length=256", port.getName());
        //emitter().emit("#pragma HLS INTERFACE m_axi port=%s_buffer offset=direct bundle=%1$s max_read_burst_length=256 max_write_burst_length=256", port.getName());
        emitter().emit("#pragma HLS INTERFACE m_axi port=ocl_buffer.data_buffer offset=direct bundle=ocl_bundle");
        emitter().emit("#pragma HLS INTERFACE m_axi port=ocl_buffer.meta_buffer offset=direct bundle=ocl_bundle");
        emitter().emit("#pragma HLS INTERFACE ap_fifo port=data_stream");
        emitter().emit("#pragma HLS INTERFACE ap_fifo port=meta_stream");
        emitter().emit("#pragma HLS INTERFACE ap_ctrl_hs register port=return");
        {
            emitter().increaseIndentation();

            emitter().emit("static iostage::InputMemoryStage< %s > i_%s_input_stage_mem;", typeStr, port.getName());
            emitter().emitNewLine();

            emitter().emit("return i_%s_input_stage_mem(ocl_buffer, fifo_count, fifo_size, data_stream, meta_stream);", port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().close();
    }

    default String entityPorts (PortDecl port, String typeStr){


        List<String> ports = new ArrayList<>();
        ports.add(String.format("iostage::CircularBuffer< %s > ocl_buffer", typeStr));

        ports.add("uint32_t fifo_count");
        ports.add("uint32_t fifo_size");

        ports.add(String.format("hls::stream< %s > &data_stream", typeStr));
        ports.add(String.format("hls::stream< bool > &meta_stream", port.getName()));
        return String.join(", ", ports);
    }

}
