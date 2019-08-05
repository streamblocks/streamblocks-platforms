package ch.epfl.vlsc.hls.backend.wrapper;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;

@Module
public interface TopKernel {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }


    default void generateTopKernel() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + "_kernel.v"));

        // -- Default net type
        emitter().emit("`default_nettype none");
        // -- Timescale
        emitter().emit("`timescale 1 ns / 1 ps");
        emitter().emitNewLine();

        // -- Kernel module
        emitter().emit("module %s_kernel #(", identifier);
        // -- Parameters
        {
            emitter().increaseIndentation();

            getParameters(network);

            emitter().decreaseIndentation();
        }
        emitter().emit(")");

        // -- Ports I/O
        emitter().emit("(");
        {
            emitter().increaseIndentation();

            getModulePortNames(network);

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
        {
            getWires(network);

            getAxiLiteControllerInstance(network);

            getKernelWrapper(network);
        }

        emitter().emitNewLine();
        emitter().emit("endmodule");
        emitter().emit("`default_nettype wire");
        emitter().close();
    }

    // ------------------------------------------------------------------------
    // -- Parameters
    default void getParameters(Network network) {

        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
                emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_DATA_WIDTH);
            }
        }
        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
                emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_DATA_WIDTH);
            }
        }
        // -- AXI4-Lite Control
        emitter().emit("parameter integer C_S_AXI_CONTROL_ADDR_WIDTH = %d,", AxiConstants.C_S_AXI_CONTROL_ADDR_WIDTH);
        emitter().emit("parameter integer C_S_AXI_CONTROL_DATA_WIDTH = %s", AxiConstants.C_S_AXI_CONTROL_DATA_WIDTH);
    }

    // ------------------------------------------------------------------------
    // -- Module Port IO

    default void getAxiMasterPorts(PortDecl port) {
        emitter().emit("// -- AXI4 Master interface : %s", port.getName());
        emitter().emit("output  wire    m_axi_%s_awvalid,", port.getName());
        emitter().emit("input   wire    m_axi_%s_awready,", port.getName());
        emitter().emit("output  wire    [C_M_AXI_%s_ADDR_WIDTH-1:0]    m_axi_%s_awaddr,", port.getName().toUpperCase(), port.getName());
        emitter().emit("output  wire    [8-1:0] m_axi_%s_awlen,", port.getName());
        emitter().emit("output  wire    m_axi_%s_wvalid,", port.getName());
        emitter().emit("input   wire    m_axi_%s_wready,", port.getName());
        emitter().emit("output  wire    [C_M_AXI_%s_DATA_WIDTH-1:0]    m_axi_%s_wdata,", port.getName().toUpperCase(), port.getName());
        emitter().emit("output  wire    [C_M_AXI_%s_DATA_WIDTH/8-1:0]  m_axi_%s_wstrb,", port.getName().toUpperCase(), port.getName());
        emitter().emit("output  wire    m_axi_%s_wlast,", port.getName());
        emitter().emit("input   wire    m_axi_%s_bvalid,", port.getName());
        emitter().emit("output  wire    m_axi_%s_bready,", port.getName());
        emitter().emit("output  wire    m_axi_%s_arvalid,", port.getName());
        emitter().emit("input   wire    m_axi_%s_arready,", port.getName());
        emitter().emit("output  wire    [C_M_AXI_%s_ADDR_WIDTH-1:0]    m_axi_%s_araddr,", port.getName().toUpperCase(), port.getName());
        emitter().emit("output  wire    [8-1:0] m_axi_%s_arlen,", port.getName());
        emitter().emit("input   wire    m_axi_%s_rvalid,", port.getName());
        emitter().emit("output  wire    m_axi_%s_rready,", port.getName());
        emitter().emit("input   wire    [C_M_AXI_%s_DATA_WIDTH-1:0]    m_axi_%s_rdata,", port.getName().toUpperCase(), port.getName());
        emitter().emit("input   wire    m_axi_%s_rlast,", port.getName());
    }


    default void getModulePortNames(Network network) {

        // -- System signals
        emitter().emit("input   wire    ap_clk,");
        emitter().emit("input   wire    ap_rst_n,");

        // -- Network input ports
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                getAxiMasterPorts(port);
            }
        }

        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                getAxiMasterPorts(port);
            }
        }

        // -- AXI4-Lite Control IO
        emitter().emit("// -- AXI4-Lite slave interface");
        // AXI4-Lite slave interface
        emitter().emit("input   wire    s_axi_control_awvalid,");
        emitter().emit("output  wire    s_axi_control_awready,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]    s_axi_control_awaddr,");
        emitter().emit("input   wire    s_axi_control_wvalid,");
        emitter().emit("output  wire    s_axi_control_wready,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_DATA_WIDTH-1:0]    s_axi_control_wdata,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_DATA_WIDTH/8-1:0]  s_axi_control_wstrb,");
        emitter().emit("input   wire    s_axi_control_arvalid,");
        emitter().emit("output  wire    s_axi_control_arready,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]    s_axi_control_araddr,");
        emitter().emit("output  wire    s_axi_control_rvalid,");
        emitter().emit("input   wire    s_axi_control_rready,");
        emitter().emit("output  wire    [C_S_AXI_CONTROL_DATA_WIDTH-1:0]    s_axi_control_rdata,");
        emitter().emit("output  wire    [2-1:0] s_axi_control_rresp,");
        emitter().emit("output  wire    s_axi_control_bvalid,");
        emitter().emit("input   wire    s_axi_control_bready,");
        emitter().emit("output  wire    [2-1:0] s_axi_control_bresp,");
        emitter().emit("output  wire    interrupt");
    }

    // ------------------------------------------------------------------------
    // -- Get wires
    default void getWires(Network network) {
        emitter().emit("reg     areset = 1'b0;");
        emitter().emit("wire    ap_start;");
        emitter().emit("wire    ap_idle;");
        emitter().emit("wire    ap_done;");
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                emitter().emit("wire    [32 - 1 : 0] %s_requested_size;", port.getName());
                emitter().emit("wire    [64 - 1 : 0] %s_size;", port.getName());
                emitter().emit("wire    [64 - 1 : 0] %s_buffer;", port.getName());
            }
        }
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit("wire    [64 - 1 : 0] %s_size;", port.getName());
                emitter().emit("wire    [64 - 1 : 0] %s_buffer;", port.getName());
            }
        }
        emitter().emitNewLine();
    }


    // ------------------------------------------------------------------------
    // -- AXI Lite controller instance
    default void getAxiLiteControllerInstance(Network network) {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emit("%s_control_s_axi #(", identifier);
        {
            emitter().increaseIndentation();
            emitter().emit(".C_ADDR_WIDTH ( C_S_AXI_CONTROL_ADDR_WIDTH ),");
            emitter().emit(".C_DATA_WIDTH ( C_S_AXI_CONTROL_DATA_WIDTH )");
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("inst_control_s_axi (");
        {
            emitter().increaseIndentation();

            emitter().emit(".aclk( ap_clk ),");
            emitter().emit(".areset( areset ),");
            emitter().emit(".aclk_en( 1'b1 ),");
            emitter().emit(".awvalid( s_axi_control_awvalid ),");
            emitter().emit(".awready( s_axi_control_awready ),");
            emitter().emit(".awaddr( s_axi_control_awaddr ),");
            emitter().emit(".wready( s_axi_control_wready ),");
            emitter().emit(".wdata( s_axi_control_wdata ),");
            emitter().emit(".wstrb( s_axi_control_wstrb ),");
            emitter().emit(".arvalid( s_axi_control_arvalid ),");
            emitter().emit(".arready( s_axi_control_arready ),");
            emitter().emit(".araddr( s_axi_control_araddr ),");
            emitter().emit(".rvalid( s_axi_control_rvalid ),");
            emitter().emit(".rready( s_axi_control_rready ),");
            emitter().emit(".rdata( s_axi_control_rdata ),");
            emitter().emit(".rresp( s_axi_control_rresp ),");
            emitter().emit(".bvalid( s_axi_control_bvalid ),");
            emitter().emit(".bready( s_axi_control_bready ),");
            emitter().emit(".bresp( s_axi_control_bresp ),");
            if (!network.getInputPorts().isEmpty()) {
                for (PortDecl port : network.getInputPorts()) {
                    emitter().emit(".%s_requested_size( %1$s_requested_size ),", port.getName());
                    emitter().emit(".%s_size( %1$s_size ),", port.getName());
                    emitter().emit(".%s_buffer( %1$s_size ),", port.getName());
                }
            }
            if (!network.getOutputPorts().isEmpty()) {
                for (PortDecl port : network.getOutputPorts()) {
                    emitter().emit(".%s_size( %1$s_size ),", port.getName());
                    emitter().emit(".%s_buffer( %1$s_size ),", port.getName());
                }
            }
            emitter().emit(".interrupt( interrupt ),");
            emitter().emit(".ap_start( ap_start ),");
            emitter().emit(".ap_done( ap_done),");
            emitter().emit(".ap_idle( ap_idle )");
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Kernel wrapper
    default void getKernelWrapper(Network network) {

    }
}
