package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;

import java.util.Optional;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.Type;

@Module
public interface Kernel {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateKernel(String kernelType) {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        Optional<ImmutableList<PortDecl>> kernelArgs = Optional.empty();
        if (kernelType.compareTo("input") == 0) {
            kernelArgs = Optional.of(network.getInputPorts());
        } else if (kernelType.compareTo("output") == 0) {
            kernelArgs = Optional.of(network.getOutputPorts());
        }
        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context())
                .resolve(identifier + "_" + kernelType + "_kernel.v"));

        // -- Default net type
        emitter().emit("`default_nettype none");
        // -- Timescale
        emitter().emit("`timescale 1 ns / 1 ps");
        emitter().emitNewLine();

        // -- Kernel module
        emitter().emit("module %s_%s_kernel #(", identifier, kernelType);
        // -- Parameters
        {
            emitter().increaseIndentation();
            
            if (kernelType == "core") {
                getMasterParameters(network, Optional.of(network.getInputPorts()), ",");
                getMasterParameters(network, Optional.of(network.getOutputPorts()), ",");
            } else {
                getMasterParameters(network, kernelArgs, ",");
            }
            getSlaveParameters(network);

            emitter().decreaseIndentation();
        }
        emitter().emit(")");

        // -- Ports I/O
        emitter().emit("(");
        {
            emitter().increaseIndentation();

            if (kernelType == "input") {
                getStreamPortNames(kernelArgs.get(), true);
            } else if (kernelType == "output") {
                getStreamPortNames(kernelArgs.get(), false);
            } else {
                getStreamPortNames(network.getInputPorts(), false);
                getStreamPortNames(network.getOutputPorts(), true);
            }

            getModulePortNames(network, kernelArgs);

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
        {
            getWires(network, kernelArgs);

            getAxiLiteControllerInstance(network, kernelArgs, kernelType == "input");

            if (kernelType == "core")
                getCoreKernelWrapper(network);
            else
                getIOKernelWrapper(network, network.getInputPorts(), kernelType);
        }

        emitter().emitNewLine();
        emitter().emit("endmodule");
        emitter().emit("`default_nettype wire");
        emitter().close();
    }

    // ------------------------------------------------------------------------
    // -- Parameters
    default void getMasterParameters(Network network, Optional<ImmutableList<PortDecl>> kernelArgs, String delimiter) {

        if (kernelArgs.isPresent()) {
            for (PortDecl port : kernelArgs.get()) {
                Type type = backend().types().declaredPortType(port);
                int bitSize = TypeUtils.sizeOfBits(type);
                boolean lastElement = (kernelArgs.get().size() - 1 == kernelArgs.get().indexOf(port));
                emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(),
                        AxiConstants.C_M_AXI_ADDR_WIDTH);
                emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(),
                        Math.max(bitSize, 32));
                emitter().emit("parameter integer C_M_AXI_%s_ID_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_AWUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_ARUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_WUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_RUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_BUSER_WIDTH =  %d%s", port.getName().toUpperCase(), 1,
                        lastElement ? delimiter : ",");

            }
        }

    }

    default void getSlaveParameters(Network network) {
        // -- AXI4-Lite Control
        emitter().emit("parameter integer C_S_AXI_CONTROL_ADDR_WIDTH = %d,",
                backend().axilitecontrolkernels().getAddressBitWidth(network));
        emitter().emit("parameter integer C_S_AXI_CONTROL_DATA_WIDTH = %s", AxiConstants.C_S_AXI_CONTROL_DATA_WIDTH);
    }

    // ------------------------------------------------------------------------
    // -- Module Port IO

    default void getAxiMasterPorts(String name) {
        emitter().emit("// -- write address channel");
        emitter().emit("output wire [C_M_AXI_%s_ID_WIDTH-1:0]     m_axi_%s_AWID,", name.toUpperCase(), name);
        emitter().emit("output wire [C_M_AXI_%s_ADDR_WIDTH-1:0]   m_axi_%s_AWADDR,", name.toUpperCase(), name);
        emitter().emit("output wire [7:0]                         m_axi_%s_AWLEN,", name);
        emitter().emit("output wire [2:0]                         m_axi_%s_AWSIZE,", name);
        emitter().emit("output wire [1:0]                         m_axi_%s_AWBURST,", name);
        emitter().emit("output wire [1:0]                         m_axi_%s_AWLOCK,", name);
        emitter().emit("output wire [3:0]                         m_axi_%s_AWCACHE,", name);
        emitter().emit("output wire [2:0]                         m_axi_%s_AWPROT,", name);
        emitter().emit("output wire [3:0]                         m_axi_%s_AWQOS,", name);
        emitter().emit("output wire [3:0]                         m_axi_%s_AWREGION,", name);
        emitter().emit("output wire [C_M_AXI_%s_AWUSER_WIDTH-1:0] m_axi_%s_AWUSER,", name.toUpperCase(), name);
        emitter().emit("output wire                               m_axi_%s_AWVALID,", name);
        emitter().emit("input  wire                               m_axi_%s_AWREADY,", name);
        emitter().emit("// -- write data channel");
        emitter().emit("output wire [C_M_AXI_%s_ID_WIDTH-1:0]     m_axi_%s_WID,", name.toUpperCase(), name);
        emitter().emit("output wire [C_M_AXI_%s_DATA_WIDTH-1:0]   m_axi_%s_WDATA,", name.toUpperCase(), name);
        emitter().emit("output wire [C_M_AXI_%s_DATA_WIDTH/8-1:0] m_axi_%s_WSTRB,", name.toUpperCase(), name);
        emitter().emit("output wire                               m_axi_%s_WLAST,", name);
        emitter().emit("output wire [C_M_AXI_%s_WUSER_WIDTH-1:0]  m_axi_%s_WUSER,", name.toUpperCase(), name);
        emitter().emit("output wire                               m_axi_%s_WVALID,", name);
        emitter().emit("input  wire                               m_axi_%s_WREADY,", name);
        emitter().emit("// -- write response channel");
        emitter().emit("input  wire [C_M_AXI_%s_ID_WIDTH-1:0]     m_axi_%s_BID,", name.toUpperCase(), name);
        emitter().emit("input  wire [1:0]                         m_axi_%s_BRESP,", name);
        emitter().emit("input  wire [C_M_AXI_%s_BUSER_WIDTH-1:0]  m_axi_%s_BUSER,", name.toUpperCase(), name);
        emitter().emit("input  wire                               m_axi_%s_BVALID,", name);
        emitter().emit("output wire                               m_axi_%s_BREADY,", name);
        emitter().emit("// -- read address channel");
        emitter().emit("output wire [C_M_AXI_%s_ID_WIDTH-1:0]     m_axi_%s_ARID,", name.toUpperCase(), name);
        emitter().emit("output wire [C_M_AXI_%s_ADDR_WIDTH-1:0]   m_axi_%s_ARADDR,", name.toUpperCase(), name);
        emitter().emit("output wire [7:0]                         m_axi_%s_ARLEN,", name);
        emitter().emit("output wire [2:0]                         m_axi_%s_ARSIZE,", name);
        emitter().emit("output wire [1:0]                         m_axi_%s_ARBURST,", name);
        emitter().emit("output wire [1:0]                         m_axi_%s_ARLOCK,", name);
        emitter().emit("output wire [3:0]                         m_axi_%s_ARCACHE,", name);
        emitter().emit("output wire [2:0]                         m_axi_%s_ARPROT,", name);
        emitter().emit("output wire [3:0]                         m_axi_%s_ARQOS,", name);
        emitter().emit("output wire [3:0]                         m_axi_%s_ARREGION,", name);
        emitter().emit("output wire [C_M_AXI_%s_ARUSER_WIDTH-1:0] m_axi_%s_ARUSER,", name.toUpperCase(), name);
        emitter().emit("output wire                               m_axi_%s_ARVALID,", name);
        emitter().emit("input  wire                               m_axi_%s_ARREADY,", name);
        emitter().emit("// -- read data channel");
        emitter().emit("input  wire [C_M_AXI_%s_ID_WIDTH-1:0]     m_axi_%s_RID,", name.toUpperCase(), name);
        emitter().emit("input  wire [C_M_AXI_%s_DATA_WIDTH-1:0]   m_axi_%s_RDATA,", name.toUpperCase(), name);
        emitter().emit("input  wire [1:0]                         m_axi_%s_RRESP,", name);
        emitter().emit("input  wire                               m_axi_%s_RLAST,", name);
        emitter().emit("input  wire [C_M_AXI_%s_RUSER_WIDTH-1:0]  m_axi_%s_RUSER,", name.toUpperCase(), name);
        emitter().emit("input  wire                               m_axi_%s_RVALID,", name);
        emitter().emit("output wire                               m_axi_%s_RREADY,", name);
    }

    // ---------------------------------------------------------------------------------------------------------
    // -- module stream ports

    default void getModulePortNames(Network network, Optional<ImmutableList<PortDecl>> kernelArgs) {

        // -- System signals
        emitter().emit("input   wire    ap_clk,");
        emitter().emit("input   wire    ap_rst_n,");

        // -- Network ports
        if (kernelArgs.isPresent()) {
            for (PortDecl port : kernelArgs.get()) {
                getAxiMasterPorts(port.getName());
            }
        }
        // getStreamMasterPorts(kernelArgs);
        // -- AXI4-Lite Control IO
        emitter().emit("// -- AXI4-Lite slave interface");
        // AXI4-Lite slave interface
        emitter().emit("input   wire    s_axi_control_AWVALID,");
        emitter().emit("output  wire    s_axi_control_AWREADY,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]    s_axi_control_AWADDR,");
        emitter().emit("input   wire    s_axi_control_WVALID,");
        emitter().emit("output  wire    s_axi_control_WREADY,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_DATA_WIDTH-1:0]    s_axi_control_WDATA,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_DATA_WIDTH/8-1:0]  s_axi_control_WSTRB,");
        emitter().emit("input   wire    s_axi_control_ARVALID,");
        emitter().emit("output  wire    s_axi_control_ARREADY,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]    s_axi_control_ARADDR,");
        emitter().emit("output  wire    s_axi_control_RVALID,");
        emitter().emit("input   wire    s_axi_control_RREADY,");
        emitter().emit("output  wire    [C_S_AXI_CONTROL_DATA_WIDTH-1:0]    s_axi_control_RDATA,");
        emitter().emit("output  wire    [2-1:0] s_axi_control_RRESP,");
        emitter().emit("output  wire    s_axi_control_BVALID,");
        emitter().emit("input   wire    s_axi_control_BREADY,");
        emitter().emit("output  wire    [2-1:0] s_axi_control_BRESP,");
        emitter().emit("output  wire    interrupt");
    }

    default void getStreamPortNames(ImmutableList<PortDecl> kernelArgs, boolean isInput) {

        emitter().emit("// -- network streams");
        for (PortDecl port : kernelArgs) {
            emitter().emit("%s\twire\t[C_M_AXI_%s_DATA_WIDTH - 1: 0]\t\t%s_TDATA, ", isInput ? "output" : "input",
                    port.getName().toUpperCase(), port.getName());
            emitter().emit("%s\twire\t\t%s_TVALID, ", isInput ? "output" : "input", port.getName());
            emitter().emit("%s\twire\t\t%s_TREADY, ", isInput ? "input" : "output", port.getName());
        }
    }

    // ------------------------------------------------------------------------
    // -- Get wires
    default void getWires(Network network, Optional<ImmutableList<PortDecl>> kernelArgs) {
        emitter().emitClikeBlockComment("Reg & Wires");
        emitter().emitNewLine();

        emitter().emit("(* DONT_TOUCH = \"yes\" *)");
        emitter().emit("reg     areset = 1'b0;");
        emitter().emit("wire    ap_start;");
        emitter().emit("wire    ap_ready;");
        emitter().emit("wire    ap_idle;");
        emitter().emit("wire    ap_done;");
        if (kernelArgs.isPresent()) {
            for (PortDecl port : kernelArgs.get()) {
                emitter().emit("wire    [32 - 1 : 0] %s_requested_size;", port.getName());
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
    default void getAxiLiteControllerInstance(Network network, Optional<ImmutableList<PortDecl>> kernelArgs,
            boolean isInput) {
        emitter().emitClikeBlockComment("AXI4-Lite Control");
        emitter().emitNewLine();
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emit("%s_control_s_axi #(", identifier);
        {
            emitter().increaseIndentation();
            emitter().emit(".C_S_AXI_ADDR_WIDTH ( C_S_AXI_CONTROL_ADDR_WIDTH ),");
            emitter().emit(".C_S_AXI_DATA_WIDTH ( C_S_AXI_CONTROL_DATA_WIDTH )");
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("inst_control_s_axi (");
        {
            emitter().increaseIndentation();

            emitter().emit(".ACLK(ap_clk),");
            emitter().emit(".ARESET(areset),");
            emitter().emit(".ACLK_EN(1'b1),");
            emitter().emit(".AWVALID(s_axi_control_AWVALID),");
            emitter().emit(".AWREADY(s_axi_control_AWREADY),");
            emitter().emit(".AWADDR(s_axi_control_AWADDR),");
            emitter().emit(".WVALID(s_axi_control_WVALID),");
            emitter().emit(".WREADY(s_axi_control_WREADY),");
            emitter().emit(".WDATA(s_axi_control_WDATA),");
            emitter().emit(".WSTRB(s_axi_control_WSTRB),");
            emitter().emit(".ARVALID(s_axi_control_ARVALID),");
            emitter().emit(".ARREADY(s_axi_control_ARREADY),");
            emitter().emit(".ARADDR(s_axi_control_ARADDR),");
            emitter().emit(".RVALID(s_axi_control_RVALID),");
            emitter().emit(".RREADY(s_axi_control_RREADY),");
            emitter().emit(".RDATA(s_axi_control_RDATA),");
            emitter().emit(".RRESP(s_axi_control_RRESP),");
            emitter().emit(".BVALID(s_axi_control_BVALID),");
            emitter().emit(".BREADY(s_axi_control_BREADY),");
            emitter().emit(".BRESP(s_axi_control_BRESP),");

            if (!kernelArgs.isEmpty()) {
                for (PortDecl port : kernelArgs.get()) {
                    emitter().emit(".%s_requested_size( %1$s_%s ),", port.getName(),
                            isInput ? "requested_size" : "available_size");
                    emitter().emit(".%s_size( %1$s_size ),", port.getName());
                    emitter().emit(".%s_buffer( %1$s_buffer ),", port.getName());
                }
            }

            emitter().emit(".interrupt( interrupt ),");
            emitter().emit(".ap_start( ap_start ),");
            emitter().emit(".ap_done( ap_done ),");
            emitter().emit(".ap_ready( ap_ready ),");
            emitter().emit(".ap_idle( ap_idle )");
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Kernel wrapper
    default void getKernelWrapper(Network network, Optional<ImmutableList<PortDecl>> kernelArgs) {
        emitter().emitClikeBlockComment("Kernel Wrapper");
        emitter().emitNewLine();

        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emit("%s_wrapper #(", identifier);
        {
            emitter().increaseIndentation();
            if (!kernelArgs.isEmpty()) {
                for (PortDecl port : kernelArgs.get()) {
                    boolean lastElement = (kernelArgs.get().size() - 1 == kernelArgs.get().indexOf(port));
                    emitter().emit(".C_M_AXI_%s_ADDR_WIDTH(C_M_AXI_%1$s_ADDR_WIDTH),", port.getName().toUpperCase());
                    emitter().emit(".C_M_AXI_%s_DATA_WIDTH(C_M_AXI_%1$s_DATA_WIDTH)%s", port.getName().toUpperCase(),
                            lastElement ? "" : ",");
                }
            }
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("inst_wrapper (");
        {
            emitter().increaseIndentation();
            emitter().emit(".ap_clk( ap_clk ),");
            emitter().emit(".ap_rst_n( ap_rst_n ),");

            if (!kernelArgs.isEmpty()) {
                for (PortDecl port : kernelArgs.get()) {
                    getAxiMasterConnection(port.getName());
                }
                for (PortDecl port : kernelArgs.get()) {
                    emitter().emit(".%s_requested_size( %1$s_requested_size ),", port.getName());
                    emitter().emit(".%s_size( %1$s_size ),", port.getName());
                    emitter().emit(".%s_buffer( %1$s_buffer ),", port.getName());
                }
            }
            emitter().emit(".ap_start( ap_start ),");
            emitter().emit(".ap_done( ap_done),");
            emitter().emit(".ap_ready( ap_ready),");
            emitter().emit(".ap_idle( ap_idle )");
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    default void getCoreKernelWrapper(Network network) {
        emitter().emitClikeBlockComment("Core kernel wrapper");
        emitter().emitNewLine();

        String identifier = backend().task().getIdentifier().getLast().toString();

        emitter().emit("%s_core_wrapper inst_core_wrapper (", identifier);
        {
            emitter().increaseIndentation();
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

    default void getIOKernelWrapper(Network network, ImmutableList<PortDecl> kernelArgs, String kernelType) {

        emitter().emitClikeBlockComment(kernelType + " kernel wrapper");
        emitter().emitNewLine();

        String identifier = backend().task().getIdentifier().getLast().toString();

        emitter().emit("%s_%s_wrapper #(", identifier, kernelType);
        {
            emitter().increaseIndentation();
            for (PortDecl port : kernelArgs) {
                boolean lastElement = (kernelArgs.size() - 1 == kernelArgs.indexOf(port));
                getBindMasterParameters(port, lastElement ? " " : ",");
            }
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("%s_inst_wrapper (", kernelType);
        {
            emitter().increaseIndentation();

            for (PortDecl port : kernelArgs) {
                getAxiMasterConnection(port.getName());
            }
            for (PortDecl port : kernelArgs) {
                emitter().emit(".%s_%s( %1$s_%2$s ),", port.getName(),
                        kernelType == "input" ? "requested_size" : "available_size");
                emitter().emit(".%s_size( %1$s_size ),", port.getName());
                emitter().emit(".%s_buffer( %1$s_buffer ),", port.getName());
            }
            for (PortDecl port : kernelArgs) {
                emitter().emit(".%s_TDATA(%1$s_TDATA)", port.getName());
                emitter().emit(".%s_TVALID(%1$s_TVALID)", port.getName());
                emitter().emit(".%s_TREADY(%1$s_TREADY)", port.getName());
            }
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

    default void getBindMasterParameters(PortDecl port, String delimiter) {
        emitter().emit(".C_M_AXI_%s_ADDR_WIDTH(C_M_AXI_%1$s_ADDR_WIDTH),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_%s_DATA_WIDTH(C_M_AXI_%1$s_DATA_WIDTH),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_%s_ID_WIDTH(C_M_AXI_%1$s_ID_WIDTH),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_%S_AWUSER_WIDTH(C_M_AXI_%1$s_AWUSER_WIDTH),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_%S_ARUSER_WIDTH(C_M_AXI_%1$s_ARUSER_WIDTH),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_%S_WUSER_WIDTH(C_M_AXI_%1$s_WUSER_WIDTH),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_%S_RUSER_WIDTH(C_M_AXI_%1$s_RUSER_WIDTH),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_%S_BUSER_WIDTH(C_M_AXI_%1$s_BUSER_WIDTH)%s", port.getName().toUpperCase(),
                delimiter);
    }
    // -- Helpers
    default void getAxiMasterConnection(String name) {
        emitter().emit(".m_axi_%s_AWVALID(m_axi_%1$s_AWVALID),", name);
        emitter().emit(".m_axi_%s_AWREADY(m_axi_%1$s_AWREADY),", name);
        emitter().emit(".m_axi_%s_AWADDR(m_axi_%1$s_AWADDR),", name);
        emitter().emit(".m_axi_%s_AWID(m_axi_%1$s_AWID),", name);
        emitter().emit(".m_axi_%s_AWLEN(m_axi_%1$s_AWLEN),", name);
        emitter().emit(".m_axi_%s_AWSIZE(m_axi_%1$s_AWSIZE),", name);
        emitter().emit(".m_axi_%s_AWBURST(m_axi_%1$s_AWBURST),", name);
        emitter().emit(".m_axi_%s_AWLOCK(m_axi_%1$s_AWLOCK),", name);
        emitter().emit(".m_axi_%s_AWCACHE(m_axi_%1$s_AWCACHE),", name);
        emitter().emit(".m_axi_%s_AWPROT(m_axi_%1$s_AWPROT),", name);
        emitter().emit(".m_axi_%s_AWQOS(m_axi_%1$s_AWQOS),", name);
        emitter().emit(".m_axi_%s_AWREGION(m_axi_%1$s_AWREGION),", name);
        emitter().emit(".m_axi_%s_AWUSER(m_axi_%1$s_AWUSER),", name);
        emitter().emit(".m_axi_%s_WVALID(m_axi_%1$s_WVALID),", name);
        emitter().emit(".m_axi_%s_WREADY(m_axi_%1$s_WREADY),", name);
        emitter().emit(".m_axi_%s_WDATA(m_axi_%1$s_WDATA),", name);
        emitter().emit(".m_axi_%s_WSTRB(m_axi_%1$s_WSTRB),", name);
        emitter().emit(".m_axi_%s_WLAST(m_axi_%1$s_WLAST),", name);
        emitter().emit(".m_axi_%s_WID(m_axi_%1$s_WID),", name);
        emitter().emit(".m_axi_%s_WUSER(m_axi_%1$s_WUSER),", name);
        emitter().emit(".m_axi_%s_ARVALID(m_axi_%1$s_ARVALID),", name);
        emitter().emit(".m_axi_%s_ARREADY(m_axi_%1$s_ARREADY),", name);
        emitter().emit(".m_axi_%s_ARADDR(m_axi_%1$s_ARADDR),", name);
        emitter().emit(".m_axi_%s_ARID(m_axi_%1$s_ARID),", name);
        emitter().emit(".m_axi_%s_ARLEN(m_axi_%1$s_ARLEN),", name);
        emitter().emit(".m_axi_%s_ARSIZE(m_axi_%1$s_ARSIZE),", name);
        emitter().emit(".m_axi_%s_ARBURST(m_axi_%1$s_ARBURST),", name);
        emitter().emit(".m_axi_%s_ARLOCK(m_axi_%1$s_ARLOCK),", name);
        emitter().emit(".m_axi_%s_ARCACHE(m_axi_%1$s_ARCACHE),", name);
        emitter().emit(".m_axi_%s_ARPROT(m_axi_%1$s_ARPROT),", name);
        emitter().emit(".m_axi_%s_ARQOS(m_axi_%1$s_ARQOS),", name);
        emitter().emit(".m_axi_%s_ARREGION(m_axi_%1$s_ARREGION),", name);
        emitter().emit(".m_axi_%s_ARUSER(m_axi_%1$s_ARUSER),", name);
        emitter().emit(".m_axi_%s_RVALID(m_axi_%1$s_RVALID),", name);
        emitter().emit(".m_axi_%s_RREADY(m_axi_%1$s_RREADY),", name);
        emitter().emit(".m_axi_%s_RDATA(m_axi_%1$s_RDATA),", name);
        emitter().emit(".m_axi_%s_RLAST(m_axi_%1$s_RLAST),", name);
        emitter().emit(".m_axi_%s_RID(m_axi_%1$s_RID),", name);
        emitter().emit(".m_axi_%s_RUSER(m_axi_%1$s_RUSER),", name);
        emitter().emit(".m_axi_%s_RRESP(m_axi_%1$s_RRESP),", name);
        emitter().emit(".m_axi_%s_BVALID(m_axi_%1$s_BVALID),", name);
        emitter().emit(".m_axi_%s_BREADY(m_axi_%1$s_BREADY),", name);
        emitter().emit(".m_axi_%s_BRESP(m_axi_%1$s_BRESP),", name);
        emitter().emit(".m_axi_%s_BID(m_axi_%1$s_BID),", name);
        emitter().emit(".m_axi_%s_BUSER(m_axi_%1$s_BUSER),", name);
    }
}
