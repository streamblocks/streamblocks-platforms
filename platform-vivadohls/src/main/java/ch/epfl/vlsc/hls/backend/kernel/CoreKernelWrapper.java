package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.Type;

import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface CoreKernelWrapper {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateCoreKernelWrapper() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenKernel(backend().context(), "core").resolve(backend().kernel().getKernelName("core") + "_wrapper.sv"));

        emitter().emitNewLine();

        // -- Network wrappermodule
        emitter().emit("module %s_core_wrapper #(", identifier);
        {
            emitter().increaseIndentation();
            backend().kernel().getMasterParameters(network, Optional.of(network.getInputPorts()), ",");
            backend().kernel().getMasterParameters(network, Optional.of(network.getOutputPorts()), ",");
            backend().kernel().getSlaveParameters(network);
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("(");
        {
            emitter().increaseIndentation();

            getModulePortNames(network);

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
        {
            emitter().increaseIndentation();
            // -- Time unit and precision
            emitter().emit("timeunit 1ps;");
            emitter().emit("timeprecision 1ps;");
            emitter().emitNewLine();

            // -- RTL Body
            emitter().emitClikeBlockComment("Begin RTL Body");
            emitter().emitNewLine();

            getWires();
            // -- AP Logic
            getApLogic(network);

            // -- Network
            getNetwork(network);
            emitter().decreaseIndentation();
        }

        emitter().emit("endmodule : %s_core_wrapper", identifier);

        emitter().close();
    }

    // ------------------------------------------------------------------------
    // -- Module port names
    default void getModulePortNames(Network network) {

        // -- stream signals
        backend().kernel().getStreamPortNames(network.getInputPorts(), false);
        backend().kernel().getStreamPortNames(network.getOutputPorts(), true);
        // -- prev stage done
        emitter().emit("input    wire prev_done,");
        // -- System signals
        emitter().emit("// -- system signals");
        emitter().emit("input   wire    ap_clk,");
        emitter().emit("input   wire    ap_rst_n,");
        emitter().emit("input   wire    ap_start,");
        emitter().emit("output  wire    ap_ready,");
        emitter().emit("output  wire    ap_idle,");
        emitter().emit("output  wire    ap_done");
        
    }

    // ------------------------------------------------------------------------
    // -- Wires
    default void getWires() {
        emitter().emit("// -- Wires and regs");
        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emit("logic   ap_start_r = 1'b0;");
        emitter().emit("wire    ap_start_pulse;");
        
    }
    // -- AP Logic
    default void getApLogic(Network network) {
        // -- pulse ap_start
        emitter().emit("// -- Pulse ap_start");
        emitter().emit("always_ff @(posedge ap_clk) begin");
        emitter().emit("\tap_start_r <= ap_start;");
        emitter().emit("end");
        emitter().emitNewLine();
        emitter().emit("assign ap_start_pulse = ap_start & ~ap_start_r;");
        emitter().emitNewLine();

    }

    // ------------------------------------------------------------------------
    // -- Network instantiation
    default void getNetwork(Network network) {
        String instanceName = backend().task().getIdentifier().getLast().toString();
        emitter().emit("%s i_%1$s(", instanceName);
        {
            emitter().increaseIndentation();
            for (PortDecl port : network.getInputPorts()) {
                emitter().emit(".%s_din(%1$s_TDATA),", port.getName());
                emitter().emit(".%s_full_n(%1$s_TREADY),", port.getName());
                emitter().emit(".%s_write(%1$s_TVALID),", port.getName());
            }
            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit(".%s_dout(%1$s_TDATA),", port.getName());
                emitter().emit(".%s_empty_n(%1$s_TVALID),", port.getName());
                emitter().emit(".%s_read(%1$s_TREADY),", port.getName());
            }
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(ap_start_pulse),");
            emitter().emit(".ap_idle(ap_idle),");
            emitter().emit(".ap_done(ap_done),");
            emitter().emit(".input_idle(prev_done)");
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

}
