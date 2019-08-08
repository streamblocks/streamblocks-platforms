package ch.epfl.vlsc.hls.backend.kernel;

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
        emitter().emitClikeBlockComment("Reg & Wires");
        emitter().emitNewLine();

        emitter().emit("(* DONT_TOUCH = \"yes\" *)");
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

        emitter().emit("// -- Invert reset signal");
        emitter().emit("always @(posedge ap_clk) begin");
        {
            emitter().increaseIndentation();
            emitter().emit(" areset <= ~ap_rst_n;");
            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- AXI Lite controller instance
    default void getAxiLiteControllerInstance(Network network) {
        emitter().emitClikeBlockComment("AXI4-Lite Control");
        emitter().emitNewLine();
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
        emitter().emitClikeBlockComment("Kernel Wrapper");
        emitter().emitNewLine();

        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emit("%s_wrapper #(", identifier);
        {
            emitter().increaseIndentation();
            if (!network.getInputPorts().isEmpty()) {
                for (PortDecl port : network.getInputPorts()) {
                    boolean lastElement = network.getOutputPorts().isEmpty() && (network.getInputPorts().size() - 1 == network.getInputPorts().indexOf(port));
                    emitter().emit(".C_M_AXI_%s_ADDR_WIDTH(C_M_AXI_%1$s_ADDR_WIDTH),", port.getName().toUpperCase());
                    emitter().emit(".C_M_AXI_%s_DATA_WIDTH(C_M_AXI_%1$s_DATA_WIDTH)%s", port.getName().toUpperCase(), lastElement ? "" : ",");
                }
            }
            // -- Network Output ports
            if (!network.getOutputPorts().isEmpty()) {
                for (PortDecl port : network.getOutputPorts()) {
                    emitter().emit(".C_M_AXI_%s_ADDR_WIDTH(C_M_AXI_%1$s_ADDR_WIDTH),", port.getName().toUpperCase());
                    emitter().emit(".C_M_AXI_%s_DATA_WIDTH(C_M_AXI_%1$s_DATA_WIDTH)%s", port.getName().toUpperCase(), network.getOutputPorts().size() - 1 == network.getOutputPorts().indexOf(port) ? "" : ",");
                }
            }
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("inst_wrapper (");
        {
            emitter().increaseIndentation();
            emitter().emit(".aclk( ap_clk ),");
            emitter().emit(".ap_rst_n( ap_rst_n ),");

            if (!network.getInputPorts().isEmpty()) {
                for (PortDecl port : network.getInputPorts()) {
                    getAxiMasterConnection(port);
                }
            }
            if (!network.getOutputPorts().isEmpty()) {
                for (PortDecl port : network.getOutputPorts()) {
                    getAxiMasterConnection(port);
                }
            }

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
            emitter().emit(".ap_start( ap_start ),");
            emitter().emit(".ap_done( ap_done),");
            emitter().emit(".ap_idle( ap_idle )");
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    // -- Helpers
    default void getAxiMasterConnection(PortDecl port){
        emitter().emit(".m_axi_%s_awvalid ( m_axi_%1$s_awvalid ),", port.getName());
        emitter().emit(".m_axi_%s_awready ( m_axi_%1$s_awready ),", port.getName());
        emitter().emit(".m_axi_%s_awaddr ( m_axi_%1$s_awaddr ),", port.getName());
        emitter().emit(".m_axi_%s_awlen ( m_axi_%1$s_awlen ),", port.getName());
        emitter().emit(".m_axi_%s_wvalid ( m_axi_%1$s_wvalid ),", port.getName());
        emitter().emit(".m_axi_%s_wready ( m_axi_%1$s_wready ),", port.getName());
        emitter().emit(".m_axi_%s_wdata ( m_axi_%1$s_wdata ),", port.getName());
        emitter().emit(".m_axi_%s_wstrb ( m_axi_%1$s_wstrb ),", port.getName());
        emitter().emit(".m_axi_%s_wlast ( m_axi_%1$s_wlast ),", port.getName());
        emitter().emit(".m_axi_%s_bvalid ( m_axi_%1$s_bvalid ),", port.getName());
        emitter().emit(".m_axi_%s_bready ( m_axi_%1$s_bready ),", port.getName());
        emitter().emit(".m_axi_%s_arvalid ( m_axi_%1$s_arvalid ),", port.getName());
        emitter().emit(".m_axi_%s_arready ( m_axi_%1$s_arready ),", port.getName());
        emitter().emit(".m_axi_%s_araddr ( m_axi_%1$s_araddr ),", port.getName());
        emitter().emit(".m_axi_%s_arlen ( m_axi_%1$s_arlen ),", port.getName());
        emitter().emit(".m_axi_%s_rvalid ( m_axi_%1$s_rvalid ),", port.getName());
        emitter().emit(".m_axi_%s_rready ( m_axi_%1$s_rready ),", port.getName());
        emitter().emit(".m_axi_%s_rdata ( m_axi_%1$s_rdata ),", port.getName());
        emitter().emit(".m_axi_%s_rlast ( m_axi_%1$s_rlast ),", port.getName());
    }
}
