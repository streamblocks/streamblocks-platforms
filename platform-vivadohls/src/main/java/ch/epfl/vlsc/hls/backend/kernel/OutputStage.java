package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.List;

@Module
public interface OutputStage {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getOutputStage(PortDecl port) {
        String identifier = port.getName();

        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + "_output_stage.v"));

        emitter().emit("`timescale 1ns / 1ps");
        emitter().emit("`define FINISH 4");
        emitter().emitNewLine();

        Type type = backend().types().declaredPortType(port);
        int bitSize = TypeUtils.sizeOfBits(type);

        emitter().emit("module %s_output_stage #(", port.getName());
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
            emitter().emit("// -- Network done");
            emitter().emit("input wire core_done,");
            backend().topkernel().getAxiMasterPorts(port.getName());
            emitter().emit("// -- Constant & Addresses");
            emitter().emit("input  wire [31:0] %s_available_size,", port.getName());
            emitter().emit("input  wire [63:0] %s_size_r,", port.getName());
            emitter().emit("input  wire [63:0] %s_buffer,", port.getName());
            emitter().emit("// -- output stream");
            emitter().emit("input wire [%d:0] %s_V_dout,", bitSize - 1, port.getName());
            emitter().emit("input  wire %s_V_empty_n,", port.getName());
            emitter().emit("output  wire %s_V_read ", port.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit(");");

        // -- Wires
        getWires(port);

        emitter().emitClikeBlockComment("Instantiations");
        emitter().emitNewLine();

        // -- Output stage pass
        backend().inputstage().getStagePass(port);

        // -- Queue
        backend().inputstage().getQueue("q_tmp", bitSize, "q_tmp_V", "q_tmp_V");

        // -- Input stage mem
        getOutputStageMem(port);

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

        // -- Output stage mem
        emitter().emit("// -- Output stage mem");
        emitter().emit("wire %s_output_stage_control_ap_done;", port.getName());
        emitter().emit("wire [31:0] %s_output_stage_control_ap_return;", port.getName());
        emitter().emitNewLine();
    }

    default void getOutputStageMem(PortDecl port) {
        emitter().emit("// -- Output stage mem for port : %s", port.getName());
        emitter().emit("%s_output_stage_mem #(", port.getName());
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
        emitter().emit("i_%s_output_stage_mem(", port.getName());
        {
            emitter().increaseIndentation();
            // -- Ap control
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(%s_output_stage_mem_ap_start),", port.getName());
            emitter().emit(".ap_done(%s_output_stage_ap_done),", port.getName());
            emitter().emit(".ap_idle(ap_idle),");
            emitter().emit(".ap_ready(ap_ready),");
            emitter().emit(".ap_return(ap_return),");
            // -- AXI Master
            backend().kernelwrapper().getAxiMasterConnections(port);
            // -- Direct address
            emitter().emit(".%s_available_size(%1$s_available_size),", port.getName());
            emitter().emit(".%s_size_r(%1$s_size),", port.getName());
            emitter().emit(".%s_buffer(%1$s_buffer),", port.getName());
            // -- FIFO I/O
            emitter().emit(".fifo_count(q_tmp_count),");
            emitter().emit(".%s_V_dout(q_tmp_V_dout),", port.getName());
            emitter().emit(".%s_V_empty_n(q_tmp_V_empty_n),", port.getName());
            emitter().emit(".%s_V_read(q_tmp_V_read)", port.getName());
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();

    }


}
