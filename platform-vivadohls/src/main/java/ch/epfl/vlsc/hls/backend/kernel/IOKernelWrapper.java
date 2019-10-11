package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Module
public interface IOKernelWrapper {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateIOKernelWrapper(boolean isInput) {

        String identifier = backend().task().getIdentifier().getLast().toString();
        Network network = backend().task().getNetwork();
        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context())
                .resolve(identifier + "_" + (isInput ? "input" : "output") + "_wrapper.sv"));

        ImmutableList<PortDecl> ports = (isInput) ? network.getInputPorts() : network.getOutputPorts();

        emitter().emitNewLine();

        emitter().emit("module %s_%s_wrapper #(", identifier, isInput ? "input" : "output");
        {
            emitter().increaseIndentation();
            backend().kernel().getMasterParameters(network, Optional.of(network.getInputPorts()), " ");
            emitter().decreaseIndentation();
        }
        emitter().emit(") ");
        // -- Ports I/O
        emitter().emit("(");
        {

            emitter().increaseIndentation();

           

            backend().kernel().getStreamPortNames(ports, isInput);
            for (PortDecl port : ports) {
                backend().kernel().getAxiMasterPorts(port.getName());
            }
            if (!isInput) {
                emitter().emit("// -- prev stage done");
                emitter().emit("input    wire prev_done,");
            }
            // -- System signals
            emitter().emit("// -- system signals");
            emitter().emit("input   wire    ap_clk,");
            emitter().emit("input   wire    ap_rst_n,");
            emitter().emit("input   wire    ap_start,");
            emitter().emit("output  wire    ap_ready,");
            emitter().emit("output  wire    ap_idle,");
            emitter().emit("output  wire    ap_done");
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
        {
            emitter().increaseIndentation();
            for (PortDecl port : ports) {
                getIOStage(port, isInput);
            }
            emitter().decreaseIndentation();
        }
        emitter().emitNewLine();
        emitter().emit("endmodule : %s_%s_wrapper", identifier, isInput ? "input" : "output");
        emitter().close();
    }

    default void getIOStage(PortDecl port, boolean isInput) {
        emitter().emit("// -- %s stage %s", isInput ? "input" : "output", port.getName());
        emitter().emitNewLine();

        emitter().emit("%s_%s_stage #(", port.getName(), isInput ? "input" : "output");
        {
            emitter().increaseIndentation();
            backend().kernel().getBindMasterParameters(port, " ");
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("%s_%s_stage_inst (", port.getName(), isInput ? "input" : "output");
        {
            emitter().increaseIndentation();
            emitter().emit("// -- AXI master interface");
            backend().kernel().getAxiMasterConnection(port.getName());

            emitter().emit("// -- kernel args");

            emitter().emit(".%s_%s(%1$s_%2$s),", port.getName(), backend().kernel().requestOrAvailable(isInput));

            emitter().emit(".%s_size(%1$s_size),", port.getName());
            emitter().emit(".%s_buffer(%1$s_buffer)", port.getName());

            emitter().emit("// -- axi stream");
            if (isInput) {
                emitter().emit(".%s_V_din(%1$s_TDATA),", port.getName());
                emitter().emit(".%s_V_full_n(%1$s_TREADY),", port.getName());
                emitter().emit(".%s_V_write(%1$s_TVALID),", port.getName());

            } else {
                emitter().emit(".%s_V_dout(%1$s_TDATA),", port.getName());
                emitter().emit(".%s_V_empty_n(%1$s_TVALID),", port.getName());
                emitter().emit(".%s_V_read(%1$s_TREADY), ", port.getName());
            }

            emitter().emit(".network_idle(prev_done),");
            emitter().emit("// -- ap control");
            emitter().emit(".ap_clk( ap_clk ),");
            emitter().emit(".ap_rst_n( ap_rst_n ),");
            emitter().emit(".ap_start( ap_start ),");
            emitter().emit(".ap_done( ap_done),");
            emitter().emit(".ap_ready( ap_ready),");
            emitter().emit(".ap_idle( ap_idle )");

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

}
