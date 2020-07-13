package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.ExternalMemory;
import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.Map;

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

        // -- External Memory
        backend().vnetwork().getExternalMemoryAxiParams(network);

        // -- Network Input ports
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                Type type = backend().types().declaredPortType(port);
                int bitSize = backend().typeseval().sizeOfBits(type);
                emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
                emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(), Math.max(bitSize, AxiConstants.IO_STAGE_BUS_WIDTH));
                emitter().emit("parameter integer C_M_AXI_%s_ID_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_AWUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_ARUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_WUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_RUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_BUSER_WIDTH =  %d,", port.getName().toUpperCase(), 1);
            }
        }
        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                Type type = backend().types().declaredPortType(port);
                int bitSize = backend().typeseval().sizeOfBits(type);
                emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
                emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(), Math.max(bitSize, AxiConstants.IO_STAGE_BUS_WIDTH));
                emitter().emit("parameter integer C_M_AXI_%s_ID_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_AWUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_ARUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_WUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_RUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
                emitter().emit("parameter integer C_M_AXI_%s_BUSER_WIDTH =  %d,", port.getName().toUpperCase(), 1);
            }
        }
        // -- AXI4-Lite Control
        emitter().emit("parameter integer C_S_AXI_CONTROL_ADDR_WIDTH = %d,", backend().axilitecontrol().getAddressBitWidth(network));
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


    default void getModulePortNames(Network network) {

        // -- System signals
        emitter().emit("input   wire    ap_clk,");
        emitter().emit("input   wire    ap_rst_n,");
        

        // -- Network external memory ports ports
        if(!backend().externalMemory().getExternalMemories(network).isEmpty()){
            for(ExternalMemory.InstanceVarDeclPair mem : backend().externalMemory().getExternalMemories(network)){
                String memName = backend().externalMemory().namePair(mem);
                getAxiMasterPorts(memName);
            }
        }

        // -- Network input ports
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                getAxiMasterPorts(port.getName());
            }
        }

        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                getAxiMasterPorts(port.getName());
            }
        }

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

    // ------------------------------------------------------------------------
    // -- Get wires
    default void getWires(Network network) {
        emitter().emitClikeBlockComment("Reg & Wires");
        emitter().emitNewLine();

        emitter().emit("(* DONT_TOUCH = \"yes\" *)");
        emitter().emit("reg     areset = 1'b0;");
        emitter().emit("wire    ap_start;");
        emitter().emit("wire    ap_ready;");
        emitter().emit("wire    ap_idle;");
        emitter().emit("wire    ap_done;");
        emitter().emit("wire    event_start;");

        ImmutableList<ExternalMemory.InstanceVarDeclPair> mems =
                backend().externalMemory().getExternalMemories(network);
        for(ExternalMemory.InstanceVarDeclPair mem: mems){
            String memName = backend().externalMemory().namePair(mem);
            emitter().emit("wire    [64 - 1 : 0] %s_offset;", memName);
            emitter().emitNewLine();
        }

        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                emitter().emit("wire    [32 - 1 : 0] %s_requested_size;", port.getName());
                emitter().emit("wire    [64 - 1 : 0] %s_size;", port.getName());
                emitter().emit("wire    [64 - 1 : 0] %s_buffer;", port.getName());
            }
        }
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit("wire    [32 - 1 : 0] %s_available_size;", port.getName());
                emitter().emit("wire    [64 - 1 : 0] %s_size;", port.getName());
                emitter().emit("wire    [64 - 1 : 0] %s_buffer;", port.getName());
            }
        }
        emitter().emitNewLine();
        emitter().emit("// -- kernel command");
        emitter().emit("wire    [64 - 1 : 0] kernel_command;");
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


            ImmutableList<ExternalMemory.InstanceVarDeclPair> mems =
                    backend().externalMemory().getExternalMemories(network);
            for(ExternalMemory.InstanceVarDeclPair mem: mems){
                String memName = backend().externalMemory().namePair(mem);
                emitter().emit(".%s_offset(%1$s_offset),", memName);
            }

            if (!network.getInputPorts().isEmpty()) {
                for (PortDecl port : network.getInputPorts()) {
                    emitter().emit(".%s_requested_size( %1$s_requested_size ),", port.getName());
                    emitter().emit(".%s_size( %1$s_size ),", port.getName());
                    emitter().emit(".%s_buffer( %1$s_buffer ),", port.getName());
                }
            }
            if (!network.getOutputPorts().isEmpty()) {
                for (PortDecl port : network.getOutputPorts()) {
                    emitter().emit(".%s_available_size( %1$s_available_size ),", port.getName());
                    emitter().emit(".%s_size( %1$s_size ),", port.getName());
                    emitter().emit(".%s_buffer( %1$s_buffer ),", port.getName());
                }
            }
            emitter().emit(".kernel_command(kernel_command),");
            emitter().emit(".interrupt( interrupt ),");
            emitter().emit(".ap_start( ap_start ),");
            emitter().emit(".ap_done( ap_done ),");
            emitter().emit(".ap_ready( ap_ready ),");
            emitter().emit(".ap_idle( ap_idle ),");
            emitter().emit(".event_start(event_start)");
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

            ImmutableList<ExternalMemory.InstanceVarDeclPair> mems =
                    backend().externalMemory().getExternalMemories(network);
            for (ExternalMemory.InstanceVarDeclPair mem : mems) {
                String memName = backend().externalMemory().namePair(mem);
                emitter().emit(".C_M_AXI_%s_ADDR_WIDTH(C_M_AXI_%1$s_ADDR_WIDTH),", memName.toUpperCase());
                emitter().emit(".C_M_AXI_%s_DATA_WIDTH(C_M_AXI_%1$s_DATA_WIDTH),", memName.toUpperCase());
            }

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
            emitter().emit(".ap_clk( ap_clk ),");
            emitter().emit(".ap_rst_n( ap_rst_n ),");

            if(!backend().externalMemory().getExternalMemories(network).isEmpty()){
                for(ExternalMemory.InstanceVarDeclPair mem : backend().externalMemory().getExternalMemories(network)){
                    String memName = backend().externalMemory().namePair(mem);
                    backend().vnetwork().getAxiMasterByPort(memName, memName);
                    emitter().emit(".%s_offset(%1$s_offset),", memName);
                    emitter().emitNewLine();
                }
            }

            if (!network.getInputPorts().isEmpty()) {
                for (PortDecl port : network.getInputPorts()) {
                    getAxiMasterConnection(port.getName());
                }
            }
            if (!network.getOutputPorts().isEmpty()) {
                for (PortDecl port : network.getOutputPorts()) {
                    getAxiMasterConnection(port.getName());
                }
            }

            if (!network.getInputPorts().isEmpty()) {
                for (PortDecl port : network.getInputPorts()) {
                    emitter().emit(".%s_requested_size( %1$s_requested_size ),", port.getName());
                    emitter().emit(".%s_size( %1$s_size ),", port.getName());
                    emitter().emit(".%s_buffer( %1$s_buffer ),", port.getName());
                }
            }
            if (!network.getOutputPorts().isEmpty()) {
                for (PortDecl port : network.getOutputPorts()) {
                    emitter().emit(".%s_available_size( %1$s_available_size ),", port.getName());
                    emitter().emit(".%s_size( %1$s_size ),", port.getName());
                    emitter().emit(".%s_buffer( %1$s_buffer ),", port.getName());
                }
            }
            emitter().emit(".kernel_command(kernel_command),");
            emitter().emit(".ap_start( ap_start ),");
            emitter().emit(".ap_done( ap_done),");
            emitter().emit(".ap_ready( ap_ready),");
            emitter().emit(".ap_idle( ap_idle ),");
            emitter().emit(".event_start(event_start)");
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
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
