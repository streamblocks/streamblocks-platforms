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

    default void getInputStage(PortDecl port) {
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + "_input_stage.v"));

        emitter().emit("`timescale 1ns / 1ps");
        emitter().emitNewLine();

        Type type = backend().types().declaredPortType(port);
        int bitSize = TypeUtils.sizeOfBits(type);

        emitter().emit("module %s_input_stage #(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
            emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d,", port.getName().toUpperCase(), Math.max(bitSize, 32));
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
            emitter().emit("// -- output stream");
            emitter().emit("output wire [%d:0] %s_V_din,", bitSize - 1, port.getName());
            emitter().emit("input  wire %s_V_full_n,", port.getName());
            emitter().emit("input  wire %s_V_write ", port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit(");");

        // -- Wires
        getWires(port);

        emitter().emitClikeBlockComment("Instantiations");
        emitter().emitNewLine();

        // -- Input stage mem
        getInputStageMem(port);

        // -- Queue
        getQueue("q_tmp", bitSize, "q_tmp_V", "q_tmp_V");

        // -- Input stage pass
        getStagePass(port);

        emitter().emit("endmodule");

        emitter().close();
    }

    default void getWires(PortDecl port) {
        emitter().emitClikeBlockComment("Reg & Wires");
        emitter().emitNewLine();

        emitter().emit("// -- Queue Buffer");
        Type type = backend().types().declaredPortType(port);
        int bitSize = TypeUtils.sizeOfBits(type);

        emitter().emit("wire [%d:0] q_tmp_V_din;", bitSize - 1);
        emitter().emit("wire q_tmp_V_full_n;");
        emitter().emit("wire q_tmp_V_write;");
        emitter().emitNewLine();
        emitter().emit("wire [%d:0] q_tmp_V_dout;", bitSize - 1);
        emitter().emit("wire q_tmp_V_empty_n;");
        emitter().emit("wire q_tmp_V_read;");
        emitter().emit("wire [31:0] q_tmp_V_count;");
        emitter().emit("wire [31:0] q_tmp_V_size;");
        emitter().emitNewLine();

        // -- Input stage mem
        emitter().emit("// -- Input stage mem");
        emitter().emit("wire %s_input_stage_mem_ap_start = ( (q_tmp_size - q_tmp_count >> 1) == (q_tmp_V_size  >> 1) ) && ap_start;", port.getName());

        emitter().emitNewLine();
    }

    default void getInputStageMem(PortDecl port) {
        emitter().emit("// -- Input stage mem for port : %s", port.getName());
        emitter().emitNewLine();

        emitter().emit("%s_input_stage_mem #(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit(".C_M_AXI_%s_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", port.getSafeName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", port.getSafeName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", port.getSafeName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", port.getSafeName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", port.getSafeName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", port.getSafeName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", port.getSafeName().toUpperCase(), port.getName().toUpperCase());
            emitter().emit(".C_M_AXI_%s_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH )", port.getSafeName().toUpperCase(), port.getName().toUpperCase());

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("i_%s_input_stage_mem(", port.getName());
        {
            emitter().increaseIndentation();
            // -- Ap control
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(%s_input_stage_mem_ap_start),", port.getName());
            emitter().emit(".ap_done(%s_input_stage_ap_done),", port.getName());
            emitter().emit(".ap_idle(ap_idle),");
            emitter().emit(".ap_ready(ap_ready),");
            emitter().emit(".ap_return(ap_return),");
            // -- AXI Master
            backend().kernelwrapper().getAxiMasterConnections(port,true);
            // -- Direct address
            emitter().emit(".%s_requested_size(%1$s_requested_size),", port.getName());
            emitter().emit(".%s_size_r(%1$s_size),", port.getName());
            emitter().emit(".%s_buffer(%1$s_buffer),", port.getName());
            // -- FIFO I/O
            emitter().emit(".fifo_count(q_tmp_count),");
            emitter().emit(".fifo_size(q_tmp_size),");
            emitter().emit(".%s_V_din(q_tmp_V_din),", port.getName());
            emitter().emit(".%s_V_full_n(q_tmp_V_full_n),", port.getName());
            emitter().emit(".%s_V_write(q_tmp_V_write)", port.getName());
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
            emitter().emit(".count(%s_count),", name);
            emitter().emit(".size(%s_size) ", name);

            emitter().decreaseIndentation();
        }

        emitter().emit(");");
        emitter().emitNewLine();
    }

    default void getStagePass(PortDecl port) {
        emitter().emit("%s_stage_pass i_%1$s_stage_pass(", port.getName());
        {
            emitter().increaseIndentation();

            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".STREAM_IN_V_dout(q_tmp_V_dout),");
            emitter().emit(".STREAM_IN_V_empty_n(q_tmp_V_empty_n),");
            emitter().emit(".STREAM_IN_V_read(q_tmp_V_read),");
            emitter().emit(".STREAM_OUT_V_din(q_tmp_V_din),");
            emitter().emit(".STREAM_OUT_V_full_n(q_tmp_V_full_n),");
            emitter().emit(".STREAM_OUT_V_write(q_tmp_V_write)");

            emitter().decreaseIndentation();
        }
        emitter().emit(");");

        emitter().emitNewLine();
    }

}
