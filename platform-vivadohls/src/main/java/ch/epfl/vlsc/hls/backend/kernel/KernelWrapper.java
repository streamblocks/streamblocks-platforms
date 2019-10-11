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

import java.util.stream.Collectors;

@Module
public interface KernelWrapper {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getKernelWrapper() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + "_wrapper.sv"));

        emitter().emit("`default_nettype none");

        emitter().emitNewLine();

        // -- Network wrappermodule
        emitter().emit("module %s_wrapper #(", identifier);
        {
            emitter().increaseIndentation();

            getParameters(network);

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

        // -- Time unit and precision
        emitter().emit("timeunit 1ps;");
        emitter().emit("timeprecision 1ps;");
        emitter().emitNewLine();

        // -- Wires and variables
        getWiresAndVariables(network);

        // -- RTL Body
        emitter().emitClikeBlockComment("Begin RTL Body");
        emitter().emitNewLine();

        // -- AP Logic
        getApLogic(network);

        // -- Input Stage(s)
        for (PortDecl port : network.getInputPorts()) {
            getInputStage(port);
        }

        // -- Network
        getNetwork(network);

        // -- Output Stage(s)
        for (PortDecl port : network.getOutputPorts()) {
            getOutputStage(port);
        }

        emitter().emit("endmodule : %s_wrapper", identifier);
        emitter().emit("`default_nettype wire");

        emitter().close();
    }

    // ------------------------------------------------------------------------
    // -- Parameters
    default void getParameters(Network network) {

        for (PortDecl port : network.getInputPorts()) {
            Type type = backend().types().declaredPortType(port);
            int bitSize = TypeUtils.sizeOfBits(type);
            boolean lastElement = network.getOutputPorts().isEmpty() && (network.getInputPorts().size() - 1 == network.getInputPorts().indexOf(port));
            emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
            emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(), Math.max(bitSize, 32));
            emitter().emit("parameter integer C_M_AXI_%s_ID_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_AWUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_ARUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_WUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_RUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_BUSER_WIDTH =  %d%s", port.getName().toUpperCase(), 1, lastElement ? "" : ",");


        }
        for (PortDecl port : network.getOutputPorts()) {
            Type type = backend().types().declaredPortType(port);
            int bitSize = TypeUtils.sizeOfBits(type);
            emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
            emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(), Math.max(bitSize, 32));
            emitter().emit("parameter integer C_M_AXI_%s_ID_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_AWUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_ARUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_WUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_RUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_BUSER_WIDTH =  %d%s", port.getName().toUpperCase(), 1, network.getOutputPorts().size() - 1 == network.getOutputPorts().indexOf(port) ? "" : ",");

        }
    }

    // ------------------------------------------------------------------------
    // -- Module port names
    default void getModulePortNames(Network network) {
        // -- System signals
        emitter().emit("input   wire    ap_clk,");
        emitter().emit("input   wire    ap_rst_n,");

        // -- Network input ports
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                backend().kernel().getAxiMasterPorts(port.getName());
            }
        }

        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                backend().kernel().getAxiMasterPorts(port.getName());
            }
        }

        // -- SDX control signals
        emitter().emit("// -- SDx Control signals");
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("input  wire    [32 - 1 : 0]    %s_requested_size,", port.getName());
            emitter().emit("input  wire    [64 - 1 : 0]    %s_size,", port.getName());
            emitter().emit("input  wire    [64 - 1 : 0]    %s_buffer,", port.getName());
        }

        // -- Network Output ports
        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("input  wire    [32 - 1 : 0]    %s_available_size,", port.getName());
            emitter().emit("input  wire    [64 - 1 : 0]    %s_size,", port.getName());
            emitter().emit("input  wire    [64 - 1 : 0]    %s_buffer,", port.getName());
        }


        emitter().emit("input   wire    ap_start,");
        emitter().emit("output  wire    ap_ready,");
        emitter().emit("output  wire    ap_idle,");
        emitter().emit("output  wire    ap_done");
    }

    // ------------------------------------------------------------------------
    // -- Wires and Variables
    default void getWiresAndVariables(Network network) {
        emitter().emitClikeBlockComment("Wires and Variables");
        emitter().emitNewLine();
        // -- AP Control
        emitter().emit("// -- AP Control");
        emitter().emit("logic   ap_start_pulse;");
        emitter().emit("logic   ap_start_r = 1'b0;");
        emitter().emitNewLine();


        // -- Network I/O
        String moduleName = backend().task().getIdentifier().getLast().toString();
        emitter().emit("// -- Network I/O for %s module", moduleName);
        for (PortDecl port : network.getInputPorts()) {
            Type type = backend().types().declaredPortType(port);
            int bitSize = TypeUtils.sizeOfBits(type);
            emitter().emit("wire    [%d:0] %s_din;", bitSize - 1, port.getName());
            emitter().emit("wire    %s_full_n;", port.getName());
            emitter().emit("wire    %s_write;", port.getName());
        }

        for (PortDecl port : network.getOutputPorts()) {
            Type type = backend().types().declaredPortType(port);
            int bitSize = TypeUtils.sizeOfBits(type);
            emitter().emit("wire    [%d:0] %s_dout;", bitSize - 1, port.getName());
            emitter().emit("wire    %s_empty_n;", port.getName());
            emitter().emit("wire    %s_read;", port.getName());
        }
        emitter().emit("wire    %s_ap_idle;", moduleName);
        emitter().emit("wire    %s_ap_done;", moduleName);
        emitter().emit("wire    %s_network_idle;", moduleName);

        // -- I/O Stage
        emitter().emit("// -- AP for I/O Stage", backend().task().getIdentifier().getLast().toString());
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("wire    %s_input_stage_ap_start;", port.getName());
            emitter().emit("wire    %s_input_stage_ap_done;", port.getName());
            emitter().emit("wire    %s_input_stage_ap_idle;", port.getName());
            emitter().emit("wire    [31:0] %s_input_stage_ap_return;", port.getName());
        }
        emitter().emitNewLine();
        emitter().emit("wire    input_stage_idle;");
        emitter().emit("wire    input_stage_done;");
        emitter().emitNewLine();
        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("wire    %s_output_stage_ap_start;", port.getName());
            emitter().emit("wire    %s_output_stage_ap_done;", port.getName());
            emitter().emit("wire    %s_output_stage_ap_idle;", port.getName());
            emitter().emit("wire    [31:0] %s_output_stage_ap_return;", port.getName());
        }
        emitter().emitNewLine();
        emitter().emit("wire    output_stage_idle;");
        emitter().emit("wire    output_stage_done;");
        emitter().emit("logic    input_stage_idle_r = 1'b1;");
        emitter().emit("logic    output_stage_idle_r = 1'b1;");
        emitter().emit("logic    %s_network_idle_r = 1'b1;", backend().task().getIdentifier().getLast().toString());
        emitter().emitNewLine();

    }

    // ------------------------------------------------------------------------
    // -- AP Logic
    default void getApLogic(Network network) {
        // -- pulse ap_start
        emitter().emit("// -- Pulse ap_start");
        emitter().emit("always_ff @(posedge ap_clk) begin");
        emitter().emit("\tap_start_r <= ap_start;");
        emitter().emit("\tinput_stage_idle_r <= input_stage_idle;");
        emitter().emit("\toutput_stage_idle_r <= output_stage_idle;");
        emitter().emit("\t%s_network_idle_r <= %s_network_idle;",
                backend().task().getIdentifier().getLast().toString(),
                backend().task().getIdentifier().getLast().toString());
        emitter().emit("end");
        emitter().emitNewLine();
        emitter().emit("assign ap_start_pulse = ap_start & ~ap_start_r;");
        emitter().emitNewLine();

        // -- ap_idle
        emitter().emit("// -- ap_idle");
        emitter().emit("assign ap_idle = %s_network_idle_r & output_stage_idle_r & input_stage_idle_r;",
                backend().task().getIdentifier().getLast().toString());
        // -- ap_read
        emitter().emit("// -- ap_ready");
        emitter().emit("assign ap_ready = input_stage_idle & ~input_stage_idle_r;");
        // -- ap_done
        emitter().emit("// -- ap_done");
        emitter().emit("assign ap_done = output_stage_idle & ~output_stage_idle_r;");
        emitter().emitNewLine();

        emitter().emitNewLine();
        emitter().emit("// -- input stage idle signal");
        emitter().emit("assign input_stage_idle = %s;", String.join(" & ", network.getInputPorts()
                .stream().map(i -> i.getName() + "_input_stage_ap_idle")
                .collect(Collectors.toList())));
        emitter().emitNewLine();


        emitter().emit("// -- output stage idle signal");
        emitter().emit("assign output_stage_idle = %s;", String.join(" & ", network.getOutputPorts()
        .stream().map(i -> i.getName() + "_output_stage_ap_idle")
        .collect(Collectors.toList())));
        emitter().emitNewLine();



        emitter().increaseIndentation();


    }

    default void getAxiMasterConnections(PortDecl port, boolean safeName) {
        if (safeName) {
            getAxiMasterByPort(port.getSafeName(), port.getName());
        } else {
            getAxiMasterByPort(port.getName(), port.getName());
        }

    }

    default void getAxiMasterByPort(String safeName, String name) {
        emitter().emit(".m_axi_%s_AWVALID(m_axi_%s_AWVALID),", safeName, name);
        emitter().emit(".m_axi_%s_AWREADY(m_axi_%s_AWREADY),", safeName, name);
        emitter().emit(".m_axi_%s_AWADDR(m_axi_%s_AWADDR),", safeName, name);
        emitter().emit(".m_axi_%s_AWID(m_axi_%s_AWID),", safeName, name);
        emitter().emit(".m_axi_%s_AWLEN(m_axi_%s_AWLEN),", safeName, name);
        emitter().emit(".m_axi_%s_AWSIZE(m_axi_%s_AWSIZE),", safeName, name);
        emitter().emit(".m_axi_%s_AWBURST(m_axi_%s_AWBURST),", safeName, name);
        emitter().emit(".m_axi_%s_AWLOCK(m_axi_%s_AWLOCK),", safeName, name);
        emitter().emit(".m_axi_%s_AWCACHE(m_axi_%s_AWCACHE),", safeName, name);
        emitter().emit(".m_axi_%s_AWPROT(m_axi_%s_AWPROT),", safeName, name);
        emitter().emit(".m_axi_%s_AWQOS(m_axi_%s_AWQOS),", safeName, name);
        emitter().emit(".m_axi_%s_AWREGION(m_axi_%s_AWREGION),", safeName, name);
        emitter().emit(".m_axi_%s_AWUSER(m_axi_%s_AWUSER),", safeName, name);
        emitter().emit(".m_axi_%s_WVALID(m_axi_%s_WVALID),", safeName, name);
        emitter().emit(".m_axi_%s_WREADY(m_axi_%s_WREADY),", safeName, name);
        emitter().emit(".m_axi_%s_WDATA(m_axi_%s_WDATA),", safeName, name);
        emitter().emit(".m_axi_%s_WSTRB(m_axi_%s_WSTRB),", safeName, name);
        emitter().emit(".m_axi_%s_WLAST(m_axi_%s_WLAST),", safeName, name);
        emitter().emit(".m_axi_%s_WID(m_axi_%s_WID),", safeName, name);
        emitter().emit(".m_axi_%s_WUSER(m_axi_%s_WUSER),", safeName, name);
        emitter().emit(".m_axi_%s_ARVALID(m_axi_%s_ARVALID),", safeName, name);
        emitter().emit(".m_axi_%s_ARREADY(m_axi_%s_ARREADY),", safeName, name);
        emitter().emit(".m_axi_%s_ARADDR(m_axi_%s_ARADDR),", safeName, name);
        emitter().emit(".m_axi_%s_ARID(m_axi_%s_ARID),", safeName, name);
        emitter().emit(".m_axi_%s_ARLEN(m_axi_%s_ARLEN),", safeName, name);
        emitter().emit(".m_axi_%s_ARSIZE(m_axi_%s_ARSIZE),", safeName, name);
        emitter().emit(".m_axi_%s_ARBURST(m_axi_%s_ARBURST),", safeName, name);
        emitter().emit(".m_axi_%s_ARLOCK(m_axi_%s_ARLOCK),", safeName, name);
        emitter().emit(".m_axi_%s_ARCACHE(m_axi_%s_ARCACHE),", safeName, name);
        emitter().emit(".m_axi_%s_ARPROT(m_axi_%s_ARPROT),", safeName, name);
        emitter().emit(".m_axi_%s_ARQOS(m_axi_%s_ARQOS),", safeName, name);
        emitter().emit(".m_axi_%s_ARREGION(m_axi_%s_ARREGION),", safeName, name);
        emitter().emit(".m_axi_%s_ARUSER(m_axi_%s_ARUSER),", safeName, name);
        emitter().emit(".m_axi_%s_RVALID(m_axi_%s_RVALID),", safeName, name);
        emitter().emit(".m_axi_%s_RREADY(m_axi_%s_RREADY),", safeName, name);
        emitter().emit(".m_axi_%s_RDATA(m_axi_%s_RDATA),", safeName, name);
        emitter().emit(".m_axi_%s_RLAST(m_axi_%s_RLAST),", safeName, name);
        emitter().emit(".m_axi_%s_RID(m_axi_%s_RID),", safeName, name);
        emitter().emit(".m_axi_%s_RUSER(m_axi_%s_RUSER),", safeName, name);
        emitter().emit(".m_axi_%s_RRESP(m_axi_%s_RRESP),", safeName, name);
        emitter().emit(".m_axi_%s_BVALID(m_axi_%s_BVALID),", safeName, name);
        emitter().emit(".m_axi_%s_BREADY(m_axi_%s_BREADY),", safeName, name);
        emitter().emit(".m_axi_%s_BRESP(m_axi_%s_BRESP),", safeName, name);
        emitter().emit(".m_axi_%s_BID(m_axi_%s_BID),", safeName, name);
        emitter().emit(".m_axi_%s_BUSER(m_axi_%s_BUSER),", safeName, name);
    }

    // ------------------------------------------------------------------------
    // -- Input Stages instantiation

    default void getInputStage(PortDecl port) {
        emitter().emit("// -- Input stage for port : %s", port.getName());
        emitter().emitNewLine();
        emitter().emit("assign %s_input_stage_ap_start = ap_start_pulse;", port.getName());
        emitter().emitNewLine();

        emitter().emit("%s_input_stage #(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit(".C_M_AXI_%s_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH )", port.getName().toUpperCase(), port.getName().toUpperCase());

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("i_%s_input_stage(", port.getName());
        {
            emitter().increaseIndentation();
            // -- Ap control
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(%s_input_stage_ap_start),", port.getName());
            emitter().emit(".ap_done(%s_input_stage_ap_done),", port.getName());
            emitter().emit(".ap_idle(%s_input_stage_ap_idle),", port.getName());
            emitter().emit(".ap_ready(),");
            emitter().emit(".ap_return(%s_input_stage_ap_return),", port.getName());
            // -- AXI Master
            getAxiMasterConnections(port, false);
            // -- Direct address
            emitter().emit(".%s_requested_size(%1$s_requested_size),", port.getName());
            emitter().emit(".%s_size_r(%1$s_size),", port.getName());
            emitter().emit(".%s_buffer(%1$s_buffer),", port.getName());
            // -- FIFO I/O
            emitter().emit(".%s_V_din(%1$s_din),", port.getName());
            emitter().emit(".%s_V_full_n(%1$s_full_n),", port.getName());
            emitter().emit(".%s_V_write(%1$s_write)", port.getName());
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
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
                emitter().emit(".%s_din(%1$s_din),", port.getName());
                emitter().emit(".%s_full_n(%1$s_full_n),", port.getName());
                emitter().emit(".%s_write(%1$s_write),", port.getName());
            }
            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit(".%s_dout(%1$s_dout),", port.getName());
                emitter().emit(".%s_empty_n(%1$s_empty_n),", port.getName());
                emitter().emit(".%s_read(%1$s_read),", port.getName());
            }
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(ap_start_pulse),");
            emitter().emit(".ap_idle(%s_network_idle),", instanceName);
            emitter().emit(".ap_done(%s_ap_done),", instanceName);
            emitter().emit(".input_idle(input_stage_idle),");
            emitter().emit(".output_idle(output_stage_idle)");
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }


    // ------------------------------------------------------------------------
    // -- Output Stages instantiation
    default void getOutputStage(PortDecl port) {
        emitter().emit("// -- Output stage for port : %s", port.getName());
        emitter().emitNewLine();

        emitter().emit("assign %s_output_stage_ap_start = ap_start_pulse;", port.getName());
        emitter().emitNewLine();

        emitter().emit("%s_output_stage #(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit(".C_M_AXI_%s_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", port.getName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH )", port.getName().toUpperCase(), port.getName().toUpperCase());

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("i_%s_output_stage(", port.getName());
        {
            emitter().increaseIndentation();
            // -- Ap control
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(%s_output_stage_ap_start),", port.getName());
            emitter().emit(".ap_done(%s_output_stage_ap_done),", port.getName());
            emitter().emit(".ap_idle(%s_output_stage_ap_idle),", port.getName());
            emitter().emit(".ap_ready(),");
            emitter().emit(".ap_return(%s_output_stage_ap_return),", port.getName());
            // -- AXI Master
            getAxiMasterConnections(port, false);
            // -- Direct address
            emitter().emit(".%s_available_size(%1$s_available_size),", port.getName());
            emitter().emit(".%s_size_r(%1$s_size),", port.getName());
            emitter().emit(".%s_buffer(%1$s_buffer),", port.getName());
            // -- FIFO I/O
            emitter().emit(".%s_V_dout(%1$s_dout),", port.getName());
            emitter().emit(".%s_V_empty_n(%1$s_empty_n),", port.getName());
            emitter().emit(".%s_V_read(%1$s_read),", port.getName());
            emitter().emit(".network_idle(input_stage_idle & %s_network_idle)", backend().task().getIdentifier().getLast().toString());
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }

}
