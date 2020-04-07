package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.type.Type;

@Module
public interface OutputStage {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default int getMinTransferSize() {
        return 4096;
    }

    default void getOutputStage(PortDecl port) {
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + "_output_stage.sv"));

        emitter().emit("`include \"TriggerTypes.sv\"");
        emitter().emit("import TriggerTypes::*;");
        emitter().emitNewLine();

        Type type = backend().types().declaredPortType(port);
        int bitSize = backend().typeseval().sizeOfBits(type);

        emitter().emit("module %s_output_stage #(", port.getName());
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
            emitter().emit("input  wire [31:0] %s_available_size,", port.getName());
            emitter().emit("input  wire [63:0] %s_size_r,", port.getName());
            emitter().emit("input  wire [63:0] %s_buffer,", port.getName());
            emitter().emit("input  wire [63:0] %s_offset,", port.getSafeName());
            emitter().emit("input  wire [31:0] kernel_command,");
            emitter().emit("// --- Trigger signals");
            backend().inputstage().getTriggerIOs(port);

            emitter().emit("// -- output stream");
            emitter().emit("input    wire [%d:0] %s_dout,", bitSize - 1, port.getName());
            emitter().emit("input    wire %s_empty_n,", port.getName());
            emitter().emit("output   wire %s_read, ", port.getName());
            emitter().emit("input    wire [31:0] %s_fifo_count, ", port.getName());
            emitter().emit("input    wire [31:0] %s_fifo_size ", port.getName());
        
            emitter().decreaseIndentation();
        }
        emitter().emit(");");

        emitter().emit("timeunit 1ps;");
        emitter().emit("timeprecision 1ps;");

        // -- Wires
        getWires(port);

        // -- fifo_count register
        getFifoCountLogic(port);

        emitter().emitClikeBlockComment("Instantiations");
        emitter().emitNewLine();

        // // -- Output stage pass
        // backend().inputstage().getStagePassNamed(String.format("%s", port.getName()),
        // String.format("%s_V", port.getName()), "q_tmp_V");

        // // -- Queue
        // backend().inputstage().getQueue("q_tmp", bitSize, "q_tmp_V", "q_tmp_V");

        // -- Output stage trigger

        getTriggerModule(port);

        // -- Output stage mem
        getOutputStageMem(port);

        // -- Output stage offset fifo

        backend().inputstage().getOffsetFifo(port);

        // -- Output stage offset fifo logic
        backend().inputstage().getOffsetFifoLogic(port);

        emitter().emitNewLine();
        
        emitter().emitNewLine();
        emitter().emit("endmodule");

        emitter().close();
    }

    default void getWires(PortDecl port) {
        emitter().emitClikeBlockComment("Reg & Wires");
        emitter().emitNewLine();

        emitter().emit("// -- Queue wires");
        emitter().emit("logic    [31:0] %s_fifo_count_reg = 32'd0;", port.getName());

        // -- Output stage mem
        getTriggerLocalWires(port);

        // --Output stage offset fifo wires
        backend().inputstage().getOffsetFifoWires(port);

    }

    default void getTriggerLocalWires(PortDecl port) {
        emitter().emit("// -- Output stage mem");
        emitter().emit("wire   %s_output_stage_ap_start;", port.getName());
        emitter().emit("wire   %s_output_stage_ap_done;", port.getName());
        emitter().emit("wire   %s_output_stage_ap_idle;", port.getName());
        emitter().emit("wire   %s_output_stage_ap_ready;", port.getName());
        emitter().emit("wire    %s_sleep;", port.getSafeName());
        emitter().emit("wire    %s_sync_wait;", port.getSafeName());
        emitter().emit("wire    %s_sync_exec;", port.getSafeName());
        emitter().emit("wire    %s_all_sleep;", port.getSafeName());

        emitter().emit("wire   [31 : 0] %s_output_stage_ap_return;", port.getName());

        emitter().emit("localparam mode_t trigger_mode = ACTOR_TRIGGER;");
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
            emitter().emit(".ap_idle(ap_idle),");
            emitter().emit(".ap_ready(ap_ready),");
            emitter().emit(".external_enqueue(1'b0),");
            emitter().emit(".all_sync(all_sync),");
            emitter().emit(".all_sync_wait(all_sync_wait),");
            emitter().emit(".all_sleep(all_sleep),");
            emitter().emit(".all_waited(all_waited),");
            emitter().emit(".sync_exec(%s_sync_exec),", port.getSafeName());
            emitter().emit(".sync_wait(%s_sync_wait),", port.getSafeName());
            emitter().emit(".waited(%s_waited),", port.getSafeName());
            emitter().emit(".sleep(%s_sleep),", port.getName());
            emitter().emit(".actor_return(%s_output_stage_ap_return),", port.getName());
            emitter().emit(".actor_done(%s_output_stage_ap_done),", port.getName());
            emitter().emit(".actor_ready(%s_output_stage_ap_ready),", port.getName());
            emitter().emit(".actor_idle(%s_output_stage_ap_idle),", port.getName());

            emitter().emit(".actor_start(%s_output_stage_ap_start)", port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }

    default void getOutputStageMem(PortDecl port) {
        emitter().emit("// -- Output stage mem for port : %s", port.getName());
        emitter().emit("%s_output_stage_mem #(", port.getName());
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
        emitter().emit("i_%s_output_stage_mem(", port.getName());
        {
            emitter().increaseIndentation();
            // -- Ap control
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(%s_output_stage_ap_start),", port.getName());
            emitter().emit(".ap_done(%s_output_stage_ap_done),", port.getName());
            emitter().emit(".ap_idle(%s_output_stage_ap_idle),", port.getName());
            emitter().emit(".ap_ready(%s_output_stage_ap_ready),", port.getName());
            emitter().emit(".ap_return(%s_output_stage_ap_return),", port.getName());
            // -- AXI Master
            backend().kernelwrapper().getAxiMasterConnections(port, true);
            // -- Direct address
            emitter().emit(".%s_available_size(%1$s_available_size),", port.getName());
            emitter().emit(".%s_size_r(%1$s_size_r),", port.getName());
            emitter().emit(".%s_buffer(%1$s_buffer),", port.getName());
            // -- FIFO I/O
            emitter().emit(".fifo_count(%s_fifo_count_reg),", port.getName());
            emitter().emit(".%s_V_dout(%1$s_dout),", port.getName());
            emitter().emit(".%s_V_empty_n(%1$s_empty_n),", port.getName());
            emitter().emit(".%s_V_read(%1$s_read),", port.getName());
            // -- offset FIFO
            emitter().emit(".%s_offset_V_dout(%s_offset_dout),", port.getName(), port.getSafeName());
            emitter().emit(".%s_offset_V_empty_n(%s_offset_empty_n),", port.getName(), port.getSafeName());
            emitter().emit(".%s_offset_V_read(%s_offset_read)", port.getName(), port.getSafeName());

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
            emitter().emit("else if(%s_output_stage_ap_idle == 1'b1 || %1$s_output_stage_ap_done == 1'b1)", port.getName());
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

}
