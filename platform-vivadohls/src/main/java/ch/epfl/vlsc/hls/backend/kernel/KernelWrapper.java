package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.Iterator;
import java.util.Map;
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
        emitter().increaseIndentation();
        {

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

            // -- Offset logic
            getOffsetLogic(network);

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

        }
        emitter().decreaseIndentation();
        emitter().emit("endmodule : %s_wrapper", identifier);
        emitter().emit("`default_nettype wire");

        emitter().close();
    }

    // ------------------------------------------------------------------------
    // -- Parameters
    default void getParameters(Network network) {

        Map<VarDecl, String> mems = backend().externalMemory().externalMemories();

        Iterator<VarDecl> itr = mems.keySet().iterator();

        while (itr.hasNext()) {
            VarDecl decl = itr.next();

            String memName = mems.get(decl);
            ListType listType = (ListType) backend().types().declaredType(decl);
            Type type = listType.getElementType();
            int bitSize = TypeUtils.sizeOfBits(type);
            boolean lastElement = network.getOutputPorts().isEmpty() && network.getInputPorts().isEmpty()
                    && !itr.hasNext();
            emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", memName.toUpperCase(),
                    AxiConstants.C_M_AXI_ADDR_WIDTH);
            emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", memName.toUpperCase(),
                    Math.max(bitSize, 32));
            emitter().emit("parameter integer C_M_AXI_%s_ID_WIDTH = %d,", memName.toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_AWUSER_WIDTH = %d,", memName.toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_ARUSER_WIDTH = %d,", memName.toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_WUSER_WIDTH = %d,", memName.toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_RUSER_WIDTH = %d,", memName.toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_BUSER_WIDTH =  %d%s", memName.toUpperCase(), 1,
                    lastElement ? "" : ",");
        }

        for (PortDecl port : network.getInputPorts()) {
            Type type = backend().types().declaredPortType(port);
            int bitSize = TypeUtils.sizeOfBits(type);
            boolean lastElement = network.getOutputPorts().isEmpty()
                    && (network.getInputPorts().size() - 1 == network.getInputPorts().indexOf(port));
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
                    lastElement ? "" : ",");
        }

        for (PortDecl port : network.getOutputPorts()) {
            Type type = backend().types().declaredPortType(port);
            int bitSize = TypeUtils.sizeOfBits(type);
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
                    network.getOutputPorts().size() - 1 == network.getOutputPorts().indexOf(port) ? "" : ",");
        }
    }

    // ------------------------------------------------------------------------
    // -- Module port names
    default void getModulePortNames(Network network) {
        // -- System signals
        emitter().emit("input   wire    ap_clk,");
        emitter().emit("input   wire    ap_rst_n,");
        emitter().emit("input   wire    event_start");

        // -- Network external memory ports ports
        if (!backend().externalMemory().externalMemories().isEmpty()) {
            for (VarDecl decl : backend().externalMemory().externalMemories().keySet()) {
                String memName = backend().externalMemory().externalMemories().get(decl);
                backend().topkernel().getAxiMasterPorts(memName);
            }
        }

        // -- Network input ports
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                backend().topkernel().getAxiMasterPorts(port.getName());
            }
        }

        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                backend().topkernel().getAxiMasterPorts(port.getName());
            }
        }

        // -- SDX control signals
        emitter().emit("// -- SDx Control signals");

        for (VarDecl decl : backend().externalMemory().externalMemories().keySet()) {
            String memName = backend().externalMemory().externalMemories().get(decl);
            emitter().emit("input  wire    [64 - 1 : 0]    %s_offset,", memName);
        }

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

        // -- Kernel command
        emitter().emit("// -- kernel command");
        emitter().emit("input  wire    [64 - 1 : 0] kernel_command,");
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
            emitter().emit("wire    [31:0] %s_fifo_count;", port.getName());
            emitter().emit("wire    [31:0] %s_fifo_size;", port.getName());
            emitter().emit("wire    [63:0] %s_offset;", port.getSafeName());
        }

        for (PortDecl port : network.getOutputPorts()) {
            Type type = backend().types().declaredPortType(port);
            int bitSize = TypeUtils.sizeOfBits(type);
            emitter().emit("wire    [%d:0] %s_dout;", bitSize - 1, port.getName());
            emitter().emit("wire    %s_empty_n;", port.getName());
            emitter().emit("wire    %s_read;", port.getName());
            emitter().emit("wire    [31:0] %s_fifo_count;", port.getName());
            emitter().emit("wire    [31:0] %s_fifo_size;", port.getName());
            emitter().emit("wire    [63:0] %s_offset;", port.getSafeName());
        }
        emitter().emit("wire    %s_ap_idle;", moduleName);
        emitter().emit("wire    %s_ap_done;", moduleName);

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
        emitter().emitNewLine();
        emitter().emit("// -- idle registers");
        emitter().emit("logic   input_stage_idle_r = 1'b1;");
        emitter().emit("logic   output_stage_idle_r = 1'b1;");
        emitter().emit("logic   %s_idle_r = 1'b1;", backend().task().getIdentifier().getLast().toString());
        emitter().emit("logic   ap_idle_r = 1'b1;");
        emitter().emitNewLine();

        emitter().emit("// -- local trigger wire");
        getPortsLocalTriggerWires(network.getInputPorts());
        getPortsLocalTriggerWires(network.getOutputPorts());

        emitter().emit("// -- global trigger wires");
        backend().vnetwork().getGlobalTriggerWires();

    }

    default void getPortsLocalTriggerWires(ImmutableList<PortDecl> ports) {
        for (PortDecl port : ports) {
            emitter().emit("wire    %s_sleep;", port.getSafeName());
            emitter().emit("wire    %s_sync_wait;", port.getSafeName());
            emitter().emit("wire    %s_sync_exec;", port.getSafeName());
            emitter().emit("wire    %s_waited;", port.getSafeName());
            emitter().emit("wire    %s_all_waited;", port.getSafeName());
        }

    }

    // ------------------------------------------------------------------------
    // -- AP Logic
    default void getApLogic(Network network) {
        // -- pulse ap_start

        emitter().emit("// -- AP control logic");

        // -- ap states
        emitter().emit("localparam [1:0] KERNEL_IDLE = 2'b00;");
        emitter().emit("localparam [1:0] KERNEL_START = 2'b01;");
        emitter().emit("localparam [1:0] KERNEL_DONE = 2'b10;");
        emitter().emit("localparam [1:0] KERNEL_ERROR = 2'b11;");
        emitter().emit("logic    [1:0] ap_state = KERNEL_IDLE");
        emitter().emit("always_ff @(posedge ap_clk) begin");
        {
            emitter().increaseIndentation();
            emitter().emit(" if(ap_rst_n == 1'b0) begin");
            {
                emitter().increaseIndentation();
                emitter().emit("ap_state <= 2'b0;");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emit("else begin");
            {   
                emitter().increaseIndentation();
                emitter().emit("case (ap_state)");
                {
                    emitter().increaseIndentation();
                    emitter().emit("KERNEL_IDLE	: if (ap_start) ap_state <= KERNEL_START;");
                    emitter().emit(
                            "KERNEL_START: if (input_stage_idle && Increment_ap_idle && output_stage_idle) ap_state <= KERNEL_START;");
                    emitter().emit("KERNEL_DONE	: ap_state <= KERNEL_IDLE;");
                    emitter().emit("KERNEL_ERROR: ap_state <= KERNEL_IDLE;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("endcase");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().decreaseIndentation();
        }
        emitter().emit("end");

        emitter().emit("assign ap_idle = ap_state == KERNEL_IDLE;");
        emitter().emit("assign ap_done = ap_state == KERNEL_DONE;");
        emitter().emit("assign ap_ready = ap_state == KERNEL_DONE;");

        emitter().emitNewLine();
        emitter().emit("// -- input stage idle signal");
        emitter().emit("assign input_stage_idle = %s;", String.join(" & ", network.getInputPorts().stream()
                .map(i -> i.getName() + "_input_stage_ap_idle").collect(Collectors.toList())));
        emitter().emitNewLine();

        emitter().emit("// -- output stage idle signal");
        emitter().emit("assign output_stage_idle = %s;", String.join(" & ", network.getOutputPorts().stream()
                .map(i -> i.getName() + "_output_stage_ap_idle").collect(Collectors.toList())));
        emitter().emitNewLine();

    }

    // -- Offset assignments

    default void getOffsetLogic(Network network) {
        emitter().emit("// -- offset logic");
        getPortOffsetLogic(network.getInputPorts());
        getPortOffsetLogic(network.getOutputPorts());
        emitter().emitNewLine();
    }

    default void getPortOffsetLogic(ImmutableList<PortDecl> ports) {
        for (PortDecl port : ports)
            emitter().emit("assign %s_offset = 64'b0;", port.getSafeName());
        emitter().emitNewLine();
    }

    default void getAxiMasterConnections(PortDecl port, boolean safeName) {
        if (safeName) {
            backend().vnetwork().getAxiMasterByPort(port.getSafeName(), port.getName());
        } else {
            backend().vnetwork().getAxiMasterByPort(port.getName(), port.getName());
        }

    }

    // ------------------------------------------------------------------------
    // -- Input Stages instantiation

    default void getInputStage(PortDecl port) {
        emitter().emit("// -- Input stage for port : %s", port.getName());
        emitter().emitNewLine();
        emitter().emit("assign %s_input_stage_ap_start = event_start;", port.getName());
        emitter().emitNewLine();

        emitter().emit("%s_input_stage #(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit(".C_M_AXI_%s_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH )", port.getName().toUpperCase(),
                    port.getName().toUpperCase());

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
            emitter().emit(".kernel_command(kernel_command[31:0]),");
            emitter().emit(".%s_offset(%1$s_offset),", port.getSafeName());

            // -- Trigger
            getTriggerGlobalBindings();
            getTriggerPortBindings(port);

            // -- FIFO I/O
            emitter().emit(".%s_din(%1$s_din),", port.getName());
            emitter().emit(".%s_full_n(%1$s_full_n),", port.getName());
            emitter().emit(".%s_write(%1$s_write),", port.getName());
            emitter().emit(".%s_fifo_count(%1$s_fifo_count),", port.getName());
            emitter().emit(".%s_fifo_size(%1$s_fifo_size)", port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Network instantiation
    default void getNetwork(Network network) {
        String instanceName = backend().task().getIdentifier().getLast().toString();

        if (backend().externalMemory().externalMemories().isEmpty()) {
            emitter().emit("%s i_%1$s(", instanceName);
        } else {
            emitter().emit("%s #(", instanceName);
            {
                emitter().increaseIndentation();

                Map<VarDecl, String> mems = backend().externalMemory().externalMemories();

                Iterator<VarDecl> itr = mems.keySet().iterator();

                while (itr.hasNext()) {
                    VarDecl decl = itr.next();
                    String memName = mems.get(decl);
                    boolean lastElement = !itr.hasNext();

                    emitter().emit(".C_M_AXI_%s_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", memName.toUpperCase(),
                            memName.toUpperCase());
                    emitter().emit(".C_M_AXI_%s_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", memName.toUpperCase(),
                            memName.toUpperCase());
                    emitter().emit(".C_M_AXI_%s_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", memName.toUpperCase(),
                            memName.toUpperCase());
                    emitter().emit(".C_M_AXI_%s_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", memName.toUpperCase(),
                            memName.toUpperCase());
                    emitter().emit(".C_M_AXI_%s_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", memName.toUpperCase(),
                            memName.toUpperCase());
                    emitter().emit(".C_M_AXI_%s_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", memName.toUpperCase(),
                            memName.toUpperCase());
                    emitter().emit(".C_M_AXI_%s_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", memName.toUpperCase(),
                            memName.toUpperCase());
                    emitter().emit(".C_M_AXI_%s_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH )%s", memName.toUpperCase(),
                            memName.toUpperCase(), lastElement ? "" : ",");
                }

                emitter().decreaseIndentation();
            }
            emitter().emit(")");
            emitter().emit("i_%s (", instanceName);
        }
        {
            emitter().increaseIndentation();
            // -- External memories
            if (!backend().externalMemory().externalMemories().isEmpty()) {
                for (VarDecl decl : backend().externalMemory().externalMemories().keySet()) {
                    String memName = backend().externalMemory().externalMemories().get(decl);
                    backend().vnetwork().getAxiMasterByPort(memName, memName);
                    emitter().emit(".%s_offset(%1$s_offset),", memName);
                    emitter().emitNewLine();
                }
            }
            // -- Input ports
            for (PortDecl port : network.getInputPorts()) {
                emitter().emit("//-- Streaming ports");
                emitter().emit(".%s_din(%1$s_din),", port.getName());
                emitter().emit(".%s_full_n(%1$s_full_n),", port.getName());
                emitter().emit(".%s_write(%1$s_write),", port.getName());
                emitter().emit(".%s_fifo_count(%1$s_fifo_count),", port.getName());
                emitter().emit(".%s_fifo_size(%1$s_fifo_size),", port.getName());
                emitter().emit("// -- trigger signals");
                emitter().emit(".%s_sleep(%1$s_sleep),", port.getSafeName());
                emitter().emit(".%s_sync_wait(%1$s_sync_wait),", port.getSafeName());
                emitter().emit(".%s_sync_exec(%1$s_sync_exec),", port.getSafeName());
                emitter().emit(".%s_waited(%1$s_waited),", port.getSafeName());
                emitter().emit(".%s_all_waited(%1$s_all_waited),", port.getSafeName());
            }
            // -- Output ports
            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit("//-- Streaming ports");
                emitter().emit(".%s_dout(%1$s_dout),", port.getName());
                emitter().emit(".%s_empty_n(%1$s_empty_n),", port.getName());
                emitter().emit(".%s_read(%1$s_read),", port.getName());
                emitter().emit(".%s_fifo_count(%1$s_fifo_count),", port.getName());
                emitter().emit(".%s_fifo_size(%1$s_fifo_size),", port.getName());
                emitter().emit("// -- trigger wires");
                emitter().emit(".%s_sleep(%1$s_sleep),", port.getSafeName());
                emitter().emit(".%s_sync_wait(%1$s_sync_wait),", port.getSafeName());
                emitter().emit(".%s_sync_exec(%1$s_sync_exec),", port.getSafeName());
                emitter().emit(".%s_waited(%1$s_waited),", port.getSafeName());
                emitter().emit(".%s_all_waited(%1$s_all_waited),", port.getSafeName());
            }
            emitter().emit("//-- global trigger signals");
            getTriggerGlobalBindings();
            // emitter().emit(".%s(%1$s);", backend().vnetwork().getAllWaitedSignal());
            emitter().emit("//-- AP control");
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(event_start),");
            emitter().emit(".ap_idle(%s_ap_idle),", instanceName);
            emitter().emit(".ap_done(%s_ap_done)", instanceName);
            // emitter().emit(".input_idle(input_stage_idle)");

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    default void getTriggerGlobalBindings() {
        emitter().emit(".%s(%1$s),", backend().vnetwork().getAllSyncSignal());
        emitter().emit(".%s(%1$s),", backend().vnetwork().getAllSyncWaitSignal());
        emitter().emit(".%s(%1$s),", backend().vnetwork().getAllSleepSignal());
    }

    default void getTriggerPortBindings(PortDecl port) {
        emitter().emit(".%s_sleep(%1$s_sleep),", port.getSafeName());
        emitter().emit(".%s_sync_wait(%1$s_sync_wait),", port.getSafeName());
        emitter().emit(".%s_sync_exec(%1$s_sync_exec),", port.getSafeName());
        emitter().emit(".%s_waited(%1$s_waited),", port.getSafeName());
        emitter().emit(".%s_all_waited(%1$s_waited),", port.getSafeName());
    }

    // ------------------------------------------------------------------------
    // -- Output Stages instantiation
    default void getOutputStage(PortDecl port) {
        emitter().emit("// -- Output stage for port : %s", port.getName());
        emitter().emitNewLine();

        emitter().emit("assign %s_output_stage_ap_start = event_start;", port.getName());
        emitter().emitNewLine();

        emitter().emit("%s_output_stage #(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit(".C_M_AXI_%s_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", port.getName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH )", port.getName().toUpperCase(),
                    port.getName().toUpperCase());

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
            emitter().emit(".kernel_command(kernel_command[63:32]),");
            emitter().emit(".%s_offset(%1$s_offset),", port.getSafeName());
            // -- Trigger
            getTriggerGlobalBindings();
            getTriggerPortBindings(port);
            // -- FIFO I/O
            emitter().emit(".%s_dout(%1$s_dout),", port.getName());
            emitter().emit(".%s_empty_n(%1$s_empty_n),", port.getName());
            emitter().emit(".%s_read(%1$s_read),", port.getName());
            emitter().emit(".%s_fifo_count(%1$s_fifo_count),", port.getName());
            emitter().emit(".%s_fifo_size(%1$s_fifo_size)", port.getName());
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }

}
