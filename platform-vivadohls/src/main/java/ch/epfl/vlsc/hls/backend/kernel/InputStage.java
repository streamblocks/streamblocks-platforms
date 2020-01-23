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
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Module
public interface InputStage {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default int getSleepTimerBits() {
        return 32;
    }

    default void getInputStage(PortDecl port) {
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + "_input_stage.sv"));

        emitter().emit("`include \"TriggerTypes.sv\"");
        emitter().emit("import TriggerTypes::*;");

        emitter().emitNewLine();

        Type type = backend().types().declaredPortType(port);
        int bitSize = TypeUtils.sizeOfBits(type);

        emitter().emit("module %s_input_stage #(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(),
                    AxiConstants.C_M_AXI_ADDR_WIDTH);
            emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(),
                    Math.max(bitSize, 32));
            emitter().emit("parameter integer C_M_AXI_%s_ID_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_AWUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_ARUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_WUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_RUSER_WIDTH = %d,", port.getName().toUpperCase(), 1);
            emitter().emit("parameter integer C_M_AXI_%s_BUSER_WIDTH =  %d", port.getName().toUpperCase(), 1);

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("(");
        {
            emitter().increaseIndentation();

            emitter().emit("input wire ap_clk,");
            emitter().emit("input wire ap_rst_n,");
            emitter().emit("// -- ap control");
            emitter().emit("input  wire ap_start,");
            emitter().emit("output wire ap_idle,");
            emitter().emit("output wire ap_ready,");
            emitter().emit("output wire ap_done,");
            emitter().emit("output wire [31:0] ap_return,");
            backend().topkernel().getAxiMasterPorts(port.getName());
            emitter().emit("// -- Constant & Addresses");
            emitter().emit("input  wire [31:0] %s_requested_size,", port.getName());
            emitter().emit("input  wire [63:0] %s_size_r,", port.getName());
            emitter().emit("input  wire [63:0] %s_buffer,", port.getName());
            emitter().emit("input  wire [63:0] %s_offset,", port.getSafeName());
            emitter().emit("input  wire [31:0] kernel_command,");
            emitter().emit("// --- Trigger signals");
            getTriggerIOs(port);
            emitter().emit("// -- output stream");
            emitter().emit("output  wire [%d:0] %s_din,", bitSize - 1, port.getName());
            emitter().emit("input   wire %s_full_n,", port.getName());
            emitter().emit("output  wire %s_write, ", port.getName());
            emitter().emit("input   wire [31:0] %s_fifo_count,", port.getName());
            emitter().emit("input   wire [31:0] %s_fifo_size", port.getName());
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().increaseIndentation();

        emitter().emit("timeunit 1ps;");
        emitter().emit("timeprecision 1ps;");
        // -- Wires
        getWires(port);

        // fifo_count reg
        getFifoCountLogic(port);

        emitter().emitClikeBlockComment("Instantiations");
        emitter().emitNewLine();

        // --- Input stage offset fifo
        getOffsetFifo(port);

        // -- Input stage trigger
        getTriggerModule(port);

        // -- Input stage mem
        getInputStageMem(port);

        // -- Offset fifo logic
        getOffsetFifoLogic(port);

        // -- Queue
        // getQueue("q_tmp", bitSize, "q_tmp_V", "q_tmp_V");

        // -- Input stage pass
        // getStagePassNamed(port.getName(), "q_tmp" + "_V", port.getName() + "_V");

        emitter().emit("assign  ap_idle = stage_idle;");

        emitter().emit("endmodule");

        emitter().close();
    }

    default void getTriggerIOs(PortDecl port) {
        // emitter().emit("// --- Trigger signals");
        emitter().emit("input  wire all_sync,");
        emitter().emit("input  wire all_sync_wait,");
        emitter().emit("input  wire all_sleep,");
        emitter().emit("input  wire all_waited,");
        emitter().emit("output wire %s_sleep,", port.getSafeName());
        emitter().emit("output wire %s_sync_wait,", port.getSafeName());
        emitter().emit("output wire %s_sync_exec,", port.getSafeName());
        emitter().emit("output wire %s_waited,", port.getSafeName());
        emitter().emit("output wire %s_all_waited,", port.getSafeName());
    }

    default void getWires(PortDecl port) {
        emitter().emitClikeBlockComment("Reg & Wires");
        emitter().emitNewLine();

        // emitter().emit("// -- Queue Buffer");
        // Type type = backend().types().declaredPortType(port);
        // int bitSize = TypeUtils.sizeOfBits(type);

        // emitter().emit("wire [%d:0] q_tmp_V_din;", bitSize - 1);
        // emitter().emit("wire q_tmp_V_full_n;");
        // emitter().emit("wire q_tmp_V_write;");
        // emitter().emitNewLine();
        // emitter().emit("wire [%d:0] q_tmp_V_dout;", bitSize - 1);
        // emitter().emit("wire q_tmp_V_empty_n;");
        // emitter().emit("wire q_tmp_V_read;");
        // emitter().emit("wire [31:0] q_tmp_V_count;");
        // emitter().emit("wire [31:0] q_tmp_V_size;");
        // emitter().emitNewLine();
        // -- fifo_count register
        getFifoCountWires(port);
        // -- Input stage mem
        emitter().emit("// -- Input stage mem");
        getTriggerLocalWires(port);
        // -- Offset fifo wires
        getOffsetFifoWires(port);
    }

    default void getFifoCountWires(PortDecl port) {
        emitter().emit("// -- fifo_count register");
        emitter().emit("logic    [31:0] %s_fifo_count_reg = 32'd0;", port.getName());
        emitter().emitNewLine();
    }

    default void getOffsetFifoWires(PortDecl port) {

        emitter().emit("// -- offset fifo wires");
        emitter().emit("wire %s_offset_empty_n;", port.getSafeName());
        emitter().emit("wire %s_offset_full_n;", port.getSafeName());
        emitter().emit("wire [63 : 0] %s_offset_dout;", port.getSafeName());
        emitter().emit("wire [63 : 0] %s_offset_din;", port.getSafeName());
        emitter().emit("wire %s_offset_read;", port.getSafeName());
        emitter().emit("wire %s_offset_write;", port.getSafeName());
        emitter().emitNewLine();
    }

    default void getTriggerLocalWires(PortDecl port) {
        emitter().emit("// -- Trigger wires for port: %s", port.getSafeName());
        emitter().emit("wire    %s_input_stage_ap_start;", port.getName());
        emitter().emit("wire    %s_input_stage_ap_done;", port.getName());
        emitter().emit("wire    %s_input_stage_ap_ready;", port.getName());
        emitter().emit("wire    %s_input_stage_ap_idle;", port.getName());
        emitter().emit("wire    [31 : 0] %s_input_stage_ap_return;", port.getName());

        // emitter().emit("wire %s_sync_wait;", port.getSafeName());
        // emitter().emit("wire %s_sync_exec;", port.getSafeName());
        // emitter().emit("wire %s_sleep;", port.getSafeName());

        emitter().emit("wire    %s_input_stage_launch_predicate;", port.getName());
        emitter().emit("wire    %s_at_least_half_empty;", port.getName());
        emitter().emit("localparam mode_t trigger_mode = ACTOR_TRIGGER;");
        // -- sleep timer for the trigger
        emitter().emit("logic    [%d - 1:0] %s_sleep_counter = %1$d'd0;", getSleepTimerBits(), port.getSafeName());

        emitter().emit("wire     stage_idle;");

        emitter().emitNewLine();
    }

    default void getTriggerModule(PortDecl port) {

        emitter().emit("// -- Trigger control for port : %s", port.getName());
        emitter().emitNewLine();

        emitter().emit("trigger #(.mode(trigger_mode)) %s_trigger (", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(ap_start),");
            emitter().emit(".ap_done(ap_done),");
            emitter().emit(".ap_idle(stage_idle),");
            emitter().emit(".ap_ready(ap_ready),");
            emitter().emit(".external_enqueue(1'b0),");
            emitter().emit(".all_sync(all_sync),");
            emitter().emit(".all_sync_wait(all_sync_wait),");
            emitter().emit(".all_sleep(all_sleep),");
            emitter().emit(".sleep(%s_sleep),", port.getSafeName());
            emitter().emit(".sync_exec(%s_sync_exec),", port.getSafeName());
            emitter().emit(".sync_wait(%s_sync_wait),", port.getSafeName());
            emitter().emit(".all_waited(%s_all_waited),", port.getSafeName());
            emitter().emit(".waited(%s_waited),", port.getSafeName());
            emitter().emit(".actor_return(%s_input_stage_ap_return),", port.getName());
            emitter().emit(".actor_done(%s_input_stage_ap_done),", port.getName());
            emitter().emit(".actor_ready(%s_input_stage_ap_ready),", port.getName());
            emitter().emit(".actor_idle(%s_input_stage_ap_idle),", port.getName());

            emitter().emit(".actor_start(%s_input_stage_ap_start)", port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
        // -- sleep timer process
        emitter().emit("// -- sleep timer");
        emitter().emit("always_ff @(posedge ap_clk) begin");
        {
            emitter().increaseIndentation();
            emitter().emit("if (ap_rst_n == 1'b0 | %s_sleep == 1'b0)", port.getSafeName());
            {
                emitter().increaseIndentation();
                emitter().emit("%s_sleep_counter <= 0;", port.getSafeName());
                emitter().decreaseIndentation();
            }
            emitter().emit("else if (%s_sleep == 1'b1)", port.getSafeName());
            {
                emitter().increaseIndentation();
                emitter().emit("%s_sleep_counter <= %1$s_sleep_counter + %d'd1;", port.getSafeName(),
                        getSleepTimerBits());
                emitter().decreaseIndentation();
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("end");

    }

    default void getInputStageMem(PortDecl port) {
        emitter().emit("// -- Input stage mem for port : %s", port.getName());
        emitter().emitNewLine();

        emitter().emit("%s_input_stage_mem #(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit(".C_M_AXI_%s_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", port.getSafeName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", port.getSafeName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", port.getSafeName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", port.getSafeName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", port.getSafeName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", port.getSafeName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", port.getSafeName().toUpperCase(),
                    port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH )", port.getSafeName().toUpperCase(),
                    port.getName().toUpperCase());

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("i_%s_input_stage_mem(", port.getName());
        {
            emitter().increaseIndentation();
            // -- Ap control
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(%s_input_stage_ap_start),", port.getName());
            emitter().emit(".ap_done(%s_input_stage_ap_done),", port.getName());
            emitter().emit(".ap_idle(%s_input_stage_ap_idle),", port.getName());
            emitter().emit(".ap_ready(%s_input_stage_ap_ready),", port.getName());
            emitter().emit(".ap_return(%s_input_stage_ap_return),", port.getName());
            // -- AXI Master
            backend().kernelwrapper().getAxiMasterConnections(port, true);
            // -- Direct address
            emitter().emit(".%s_requested_size(%1$s_requested_size),", port.getName());
            emitter().emit(".%s_size_r(%1$s_size_r),", port.getName());
            emitter().emit(".%s_buffer(%1$s_buffer),", port.getName());
            // -- FIFO I/O
            emitter().emit(".fifo_count(%1$s_fifo_count_reg),", port.getName());
            emitter().emit(".fifo_size(%1$s_fifo_size),", port.getName());
            emitter().emit(".%s_V_din(%1$s_din),", port.getName());
            emitter().emit(".%s_V_full_n(%1$s_full_n),", port.getName());
            emitter().emit(".%s_V_write(%1$s_write),", port.getName());
            // -- offset FIFO
            emitter().emit(".%s_offset_V_dout(%s_offset_dout),", port.getName(), port.getSafeName());
            emitter().emit(".%s_offset_V_empty_n(%s_offset_empty_n),", port.getName(), port.getSafeName());
            emitter().emit(".%s_offset_V_read(%s_offset_read)", port.getName(), port.getSafeName());
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }

    default void getQueue(String name, int dataWidth, String inputName, String outputName) {
        emitter().emit("FIFO #(");
        {
            emitter().increaseIndentation();

            emitter().emit(".MEM_STYLE(\"block\"),");
            emitter().emit(".DATA_WIDTH(%d),", dataWidth);
            emitter().emit(".ADDR_WIDTH(12)");

            emitter().decreaseIndentation();
        }
        emitter().emit(") %s(", name);
        {
            emitter().increaseIndentation();

            emitter().emit(".clk(ap_clk),");
            emitter().emit(".reset_n(ap_rst_n),");
            emitter().emit(".if_full_n(%s_full_n),", inputName);
            emitter().emit(".if_write(%s_write),", inputName);
            emitter().emit(".if_din(%s_din),", inputName);
            emitter().emitNewLine();
            emitter().emit(".if_empty_n(%s_empty_n),", outputName);
            emitter().emit(".if_read(%s_read),", outputName);
            emitter().emit(".if_dout(%s_dout),", outputName);
            emitter().emitNewLine();
            emitter().emit(".peek(),");
            emitter().emit(".count(%s_V_count),", name);
            emitter().emit(".size(%s_V_size) ", name);

            emitter().decreaseIndentation();
        }

        emitter().emit(");");
        emitter().emitNewLine();
    }

    default void getFifoCountLogic(PortDecl port) {
        emitter().emit("// -- FIFO count sampling");
        emitter().emit("always_ff @(posedge ap_clk) begin");
        {
            emitter().increaseIndentation();
            emitter().emit("if (ap_rst_n == 1'b0)");
            {
                emitter().increaseIndentation();
                emitter().emit("%s_fifo_count_reg <= 0;", port.getName());
                emitter().decreaseIndentation();
            }
            emitter().emit("else if(%s_input_stage_ap_idle == 1'b1 || %1$s_input_stage_ap_done == 1'b1)", port.getName());
            {
                emitter().increaseIndentation();
                emitter().emit("%s_fifo_count_reg <= %1$s_fifo_count;", port.getName());
                emitter().decreaseIndentation();
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();
    }

    default void getOffsetFifo(PortDecl port) {

        emitter().emit("// --- offset FIFO");

        emitter().emit("FIFO #(");
        {
            emitter().increaseIndentation();

            emitter().emit(".MEM_STYLE(\"auto\"),");
            emitter().emit(".DATA_WIDTH(64),");
            emitter().emit(".ADDR_WIDTH(1)");

            emitter().decreaseIndentation();
        }
        emitter().emit(") %s_offset_fifo(", port.getSafeName());
        {
            emitter().increaseIndentation();

            emitter().emit(".clk(ap_clk),");
            emitter().emit(".reset_n(ap_rst_n),");
            emitter().emit(".if_full_n(%s_offset_full_n),", port.getSafeName());
            emitter().emit(".if_write(%s_offset_write),", port.getSafeName());
            emitter().emit(".if_din(%s_offset_din),", port.getSafeName());
            emitter().emitNewLine();
            emitter().emit(".if_empty_n(%s_offset_empty_n),", port.getSafeName());
            emitter().emit(".if_read(%s_offset_read),", port.getSafeName());
            emitter().emit(".if_dout(%s_offset_dout),", port.getSafeName());
            emitter().emitNewLine();
            emitter().emit(".peek(),");
            emitter().emit(".count(),");
            emitter().emit(".size() ");

            emitter().decreaseIndentation();
        }

        emitter().emit(");");
        emitter().emitNewLine();
    }

    default void getOffsetFifoLogic(PortDecl port) {
        emitter().emit("// -- Offset fifo (initialization) logic");

        emitter().emit("assign %s_offset_write = ap_start;", port.getSafeName());
        emitter().emit("assign %s_offset_din = %1$s_offset;", port.getSafeName());

    }
}
