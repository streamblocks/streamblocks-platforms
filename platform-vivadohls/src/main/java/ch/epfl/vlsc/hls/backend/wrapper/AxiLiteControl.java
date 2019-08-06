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
public interface AxiLiteControl {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getAxiLiteControl() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + "_control_s_axi.v"));

        // -- Default net type
        emitter().emit("`default_nettype none");
        // -- Timescale
        emitter().emit("`timescale 1 ns / 1 ps");
        emitter().emitNewLine();

        // -- AXI4-Lite Control module
        emitter().emit("module %s_control_s_axi #(", identifier);
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

            getAxiSlaveIO(network);

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();

        // -- Local parameters
        getLocalParameters(network);

        // -- Wires and variables
        getWiresAndVariables(network);

        // -- RTL Body
        emitter().emitClikeBlockComment("Begin RTL Body");
        emitter().emitNewLine();

        // -- AXI Write FSM
        getAxiWriteFSM();

        // -- AXI Read FSM
        getAxiReadFSM(network);

        // -- Register Logic
        getRegisterLogic(network);

        emitter().emitNewLine();
        emitter().emit("endmodule");
        emitter().emit("`default_nettype wire");
        emitter().close();

    }

    default void getParameters(Network network) {
        // -- AXI4-Lite Address and data width
        emitter().emit("parameter integer C_S_AXI_CONTROL_ADDR_WIDTH = %d,", AxiConstants.C_S_AXI_CONTROL_ADDR_WIDTH);
        emitter().emit("parameter integer C_S_AXI_CONTROL_DATA_WIDTH = %s", AxiConstants.C_S_AXI_CONTROL_DATA_WIDTH);
    }

    default void getAxiSlaveIO(Network network) {
        // -- System signals
        emitter().emit("input   wire    ap_clk,");
        emitter().emit("input   wire    ap_rst_n,");

        emitter().emit("input   wire    awvalid,");
        emitter().emit("output  wire    awready,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]    awaddr,");
        emitter().emit("input   wire    wvalid,");
        emitter().emit("output  wire    wready,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_DATA_WIDTH-1:0]    wdata,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_DATA_WIDTH/8-1:0]  wstrb,");
        emitter().emit("input   wire    arvalid,");
        emitter().emit("output  wire    arready,");
        emitter().emit("input   wire    [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]    araddr,");
        emitter().emit("output  wire    rvalid,");
        emitter().emit("input   wire    rready,");
        emitter().emit("output  wire    [C_S_AXI_CONTROL_DATA_WIDTH-1:0]    rdata,");
        emitter().emit("output  wire    [2-1:0] rresp,");
        emitter().emit("output  wire    bvalid,");
        emitter().emit("input   wire    bready,");
        emitter().emit("output  wire    [2-1:0] bresp,");
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                emitter().emit("output  wire    [32 - 1 : 0]    %s_requested_size,", port.getName());
                emitter().emit("output  wire    [64 - 1 : 0]    %s_size,", port.getName());
                emitter().emit("output  wire    [64 - 1 : 0]    %s_buffer,", port.getName());
            }
        }

        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit("output  wire    [64 - 1 : 0]    %s_size,", port.getName());
                emitter().emit("output  wire    [64 - 1 : 0]    %s_buffer,", port.getName());
            }
        }
        emitter().emit("output  wire    interrupt");

    }

    default void getLocalParameters(Network network) {
        emitter().emitClikeBlockComment("Local Parameters");
        emitter().emitNewLine();
        int value = 0;
        emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_AP_CTRL = 12'h%s;", String.format("%03x", value));
        value += 4;
        emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_GIE = 12'h%s;", String.format("%03x", value));
        value += 4;
        emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_IER = 12'h%s;", String.format("%03x", value));
        value += 4;
        emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_ISR = 12'h%s;", String.format("%03x", value));
        value += 4;
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_REQUESTED_SIZE_0 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
        }
        value += 8;
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_size_0 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
            value += 4;
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_size_1 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
            value += 4;
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_buffer_0 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
            value += 4;
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_buffer_1 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
            value += 4;
        }
        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_size_0 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
            value += 4;
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_size_1 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
            value += 4;
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_buffer_0 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
            value += 4;
            emitter().emit("localparam  [C_ADDR_WIDTH-1:0]  LP_ADDR_%s_buffer_1 = 12'h%s;", port.getName().toUpperCase(), String.format("%03x", value));
            value += 4;
        }

        emitter().emit("localparam  integer LP_SM_WIDTH = 2;");
        emitter().emit("localparam  [LP_SM_WIDTH-1:0]   SM_WRIDLE   = 2'd0;");
        emitter().emit("localparam  [LP_SM_WIDTH-1:0]   SM_WRDATA   = 2'd1;");
        emitter().emit("localparam  [LP_SM_WIDTH-1:0]   SM_WRRESP   = 2'd2;");
        emitter().emit("localparam  [LP_SM_WIDTH-1:0]   SM_WRRESET  = 2'd3;");
        emitter().emit("localparam  [LP_SM_WIDTH-1:0]   SM_RDIDLE   = 2'd0;");
        emitter().emit("localparam  [LP_SM_WIDTH-1:0]   SM_RDDATA   = 2'd1;");
        emitter().emit("localparam  [LP_SM_WIDTH-1:0]   SM_RDRESET  = 2'd3;");
        emitter().emitNewLine();
    }

    default void getWiresAndVariables(Network network) {
        emitter().emitClikeBlockComment("Wires and Variables");
        emitter().emitNewLine();

        emitter().emit("reg   [LP_SM_WIDTH-1:0]   wstate  = SM_WRRESET;");
        emitter().emit("reg   [LP_SM_WIDTH-1:0]   wnext;");
        emitter().emit("reg   [C_ADDR_WIDTH-1:0]  waddr;");
        emitter().emit("wire  [C_DATA_WIDTH-1:0]  wmask;");
        emitter().emit("wire  aw_hs;");
        emitter().emit("wire  w_hs;");
        emitter().emit("reg   [LP_SM_WIDTH-1:0]   rstate  = SM_RDRESET;");
        emitter().emit("reg   [LP_SM_WIDTH-1:0]   rnext;");
        emitter().emit("reg   [C_DATA_WIDTH-1:0]  rdata_r;");
        emitter().emit("wire  ar_hs;");
        emitter().emit("wire  [C_ADDR_WIDTH-1:0]  raddr;");
        emitter().emitNewLine();

        emitter().emit("// -- Internal Registers");
        emitter().emit("wire  int_ap_idle;");
        emitter().emit("reg   int_ap_done = 1'b0;");
        emitter().emit("reg   int_ap_start    = 1'b0;");
        emitter().emit("reg   int_gie = 1'b0;");
        emitter().emit("reg   int_ier = 1'b0;");
        emitter().emit("reg   int_isr = 1'b0;");
        emitter().emitNewLine();

        emitter().emit("// -- Internal Registers for addresses for I/O");
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("reg [32 - 1 : 0]    int_%s_requested_size = 32'd0;", port.getName());
            emitter().emit("reg [64 - 1 : 0]    int_%s_size = 64'd0;", port.getName());
            emitter().emit("reg [64 - 1 : 0]    int_%s_buffer = 64'd0;", port.getName());
        }
        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("reg [64 - 1 : 0]    int_%s_size = 64'd0;", port.getName());
            emitter().emit("reg [64 - 1 : 0]    int_%s_buffer = 64'd0;", port.getName());
        }
        emitter().emitNewLine();
    }

    default void getAxiWriteFSM() {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- AXI Write FSM");
        emitter().emit("assign awready = (wstate == SM_WRIDLE);");
        emitter().emit("assign wready  = (wstate == SM_WRDATA);");
        emitter().emit("assign bresp   = 2'b00;  // -- OKAY");
        emitter().emit("assign bvalid  = (wstate == SM_WRRESP);");
        emitter().emit("assign wmask   = { {8{wstrb[3]}}, {8{wstrb[2]}}, {8{wstrb[1]}}, {8{wstrb[0]}} };");
        emitter().emit("assign aw_hs   = awvalid & awready;");
        emitter().emit("assign w_hs    = wvalid & wready;");
        emitter().emitNewLine();

        // -- Write state
        emitter().emit("// -- Write state");
        emitter().emit("always @(posedge aclk) begin");
        emitter().increaseIndentation();
        {
            emitter().emit("if (areset)");
            {
                emitter().increaseIndentation();

                emitter().emit("wstate <= SM_WRRESET;");

                emitter().decreaseIndentation();
            }
            emitter().emit(" else if (aclk_en)");
            {
                emitter().increaseIndentation();

                emitter().emit("wstate <= wnext;");

                emitter().decreaseIndentation();
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("end");
        emitter().emitNewLine();


        // -- Write next
        emitter().emit("// -- Write next");
        emitter().emit("always @(*) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("case (wstate)");
            {
                emitter().increaseIndentation();

                emitter().emit("SM_WRIDLE:");
                {
                    emitter().increaseIndentation();
                    emitter().emit("if (awvalid)");
                    emitter().emit("\twnext = SM_WRDATA;");
                    emitter().emit("else");
                    emitter().emit("\twnext = SM_WRIDLE;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("SM_WRDATA:");
                {
                    emitter().increaseIndentation();
                    emitter().emit("if (wvalid)");
                    emitter().emit("\twnext = SM_WRRESP;");
                    emitter().emit("else");
                    emitter().emit("\twnext = SM_WRDATA;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("SM_WRRESP:");
                {
                    emitter().increaseIndentation();
                    emitter().emit("if (bready)");
                    emitter().emit("\twnext = SM_WRIDLE;");
                    emitter().emit("else");
                    emitter().emit("\twnext = SM_WRRESP;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("default:");
                emitter().emit("\twnext = SM_WRIDLE;");

                emitter().decreaseIndentation();
            }
            emitter().emit("endcase");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        emitter().emit("// -- Write address");
        emitter().emit("always @(posedge aclk) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (aclk_en) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (aw_hs)");
                emitter().emit("\twaddr <= awaddr;");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();
    }

    default void getAxiReadFSM(Network network) {
        emitter().emit("// -- AXI Read FSM");

        emitter().emit("assign  arready = (rstate == SM_RDIDLE);");
        emitter().emit("assign  rdata   = rdata_r;");
        emitter().emit("assign  rresp   = 2'b00;  // OKAY");
        emitter().emit("assign  rvalid  = (rstate == SM_RDDATA);");
        emitter().emit("assign  ar_hs   = arvalid & arready;");
        emitter().emit("assign  raddr   = araddr;");
        emitter().emitNewLine();

        emitter().emit("// -- Read state");
        emitter().emit("always @(posedge aclk) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (areset)");
            emitter().emit("\trstate <= SM_RDRESET;");
            emitter().emit("else if (aclk_en)");
            emitter().emit("\trstate <= rnext;");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        emitter().emit("// -- Read next");
        emitter().emit("always @(*) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("case (rstate)");
            {
                emitter().increaseIndentation();
                // -- SM_RDIDLE
                emitter().emit("SM_RDIDLE:");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (arvalid)");
                    emitter().emit("\trnext = SM_RDDATA;");
                    emitter().emit("else");
                    emitter().emit("\trnext = SM_RDIDLE;");

                    emitter().decreaseIndentation();
                }

                // -- SM_RDDATA
                emitter().emit("SM_RDDATA:");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (rready & rvalid)");
                    emitter().emit("\trnext = SM_RDIDLE;");
                    emitter().emit("else");
                    emitter().emit("\trnext = SM_RDDATA;");

                    emitter().decreaseIndentation();
                }

                // -- default
                emitter().emit("default:");
                emitter().emit("\t rnext = SM_RDIDLE;");

                emitter().decreaseIndentation();
            }
            emitter().emit("endcase");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        emitter().emit("// -- Read data");
        emitter().emit("always @(posedge aclk) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (aclk_en) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (ar_hs) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("rdata_r <= {C_DATA_WIDTH{1'b0}};");
                    emitter().emit("case (raddr)");
                    {
                        emitter().increaseIndentation();

                        // -- LP_ADDR_AP_CTRL
                        emitter().emit("LP_ADDR_AP_CTRL: begin");
                        {
                            emitter().increaseIndentation();

                            emitter().emit("rdata_r[0] <= int_ap_start;");
                            emitter().emit("rdata_r[1] <= int_ap_done;");
                            emitter().emit("rdata_r[2] <= int_ap_idle;");
                            emitter().emit("rdata_r[3+:C_DATA_WIDTH-3] <= {C_DATA_WIDTH-3{1'b0}};");

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("end");

                        // -- LP_ADDR_GIE
                        emitter().emit("LP_ADDR_GIE: begin");
                        {
                            emitter().increaseIndentation();

                            emitter().emit("rdata_r[0] <= int_gie;");
                            emitter().emit("rdata_r[1+:C_DATA_WIDTH-1] <= {C_DATA_WIDTH-1{1'b0}};");

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("end");

                        // -- LP_ADDR_IER
                        emitter().emit("LP_ADDR_IER: begin");
                        {
                            emitter().increaseIndentation();

                            emitter().emit("rdata_r[0] <= int_ier;");
                            emitter().emit("rdata_r[1+:C_DATA_WIDTH-1] <=  {C_DATA_WIDTH-1{1'b0}};");

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("end");

                        // -- LP_ADDR_IER
                        emitter().emit("LP_ADDR_ISR: begin");
                        {
                            emitter().increaseIndentation();

                            emitter().emit("rdata_r[0] <= int_isr;");
                            emitter().emit("rdata_r[1+:C_DATA_WIDTH-1] <=  {C_DATA_WIDTH-1{1'b0}};");

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("end");

                        for (PortDecl port : network.getInputPorts()) {
                            emitter().emit("LP_ADDR_%s_REQUESTED_SIZE_0: begin", port.getName().toUpperCase());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_requested_size[0+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("LP_ADDR_%s_size_0: begin", port.getName());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_size[0+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("LP_ADDR_%s_size_1: begin", port.getName());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_size[32+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("LP_ADDR_%s_buffer_0: begin", port.getName());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_buffer[0+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("LP_ADDR_%s_buffer_1: begin", port.getName());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_buffer[32+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");
                        }

                        for (PortDecl port : network.getOutputPorts()) {
                            emitter().emit("LP_ADDR_%s_size_0: begin", port.getName());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_size[0+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("LP_ADDR_%s_size_1: begin", port.getName());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_size[32+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("LP_ADDR_%s_buffer_0: begin", port.getName());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_buffer[0+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("LP_ADDR_%s_buffer_1: begin", port.getName());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata_r <= int_%s_buffer[32+:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");
                        }

                        // -- default
                        emitter().emit("default: begin");
                        emitter().emit("\trdata_r <= {C_DATA_WIDTH{1'b0}};");
                        emitter().emit("end");

                        emitter().decreaseIndentation();
                    }
                    emitter().emit("endcase");

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();
    }

    default void getRegisterLogic(Network network){
        emitter().emitClikeBlockComment("Register Logic");
        emitter().emit("assign interrupt    = int_gie & (|int_isr);");
        emitter().emit("assign ap_start     = int_ap_start;");
        emitter().emit("assign int_ap_idle  = ap_idle;");
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("assign %s_requested_size = int_%1$s_requested_size;", port.getName());
            emitter().emit("assign %s_size = int_%1$s_size;", port.getName());
            emitter().emit("assign %s_buffer = int_%1$s_buffer;", port.getName());
        }
        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("assign %s_size = int_%1$s_size;", port.getName());
            emitter().emit("assign %s_buffer = int_%1$s_buffer;", port.getName());
        }
        emitter().emitNewLine();

        // -- int_ap_start
        emitter().emit("// -- int_ap_start");
        emitter().emit("always @(posedge aclk) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (areset)");
            emitter().emit("\tint_ap_done <= 1'b0;");
            emitter().emit("else if (aclk_en) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (ap_done)");
                emitter().emit("\tint_ap_done <= 1'b1");
                emitter().emit("else if (ar_hs && raddr == LP_ADDR_AP_CTRL)");
                emitter().emit("\tint_ap_done <= 1'b0; // clear on read");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_gie
        emitter().emit("// -- int_gie");
        emitter().emit("always @(posedge aclk) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (areset)");
            emitter().emit("\tint_gie <= 1'b0;");
            emitter().emit("else if (aclk_en) begin");
            {
                emitter().increaseIndentation();
                emitter().emit("if (w_hs && waddr == LP_ADDR_GIE && wstrb[0])");
                emitter().emit("\t int_gie <= wdata[0];");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_ier
        emitter().emit("// -- int_ier");
        emitter().emit("always @(posedge aclk) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (areset)");
            emitter().emit("\tint_ier <= 1'b0;");
            emitter().emit("else if (aclk_en) begin");
            {
                emitter().increaseIndentation();
                emitter().emit("if (w_hs && waddr == LP_ADDR_IER && wstrb[0])");
                emitter().emit("\t int_ier <= wdata[0];");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_isr
        emitter().emit("// -- int_isr");
        emitter().emit("always @(posedge aclk) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (areset)");
            emitter().emit("\tint_isr <= 1'b0;");
            emitter().emit("else if (aclk_en) begin");
            {
                emitter().increaseIndentation();
                emitter().emit("if (int_ier & ap_done)");
                emitter().emit("\tint_isr <= 1'b1;");
                emitter().emit("else if (w_hs && waddr == LP_ADDR_ISR && wstrb[0])");
                emitter().emit("\tint_isr <= int_isr ^ wdata[0];");
                emitter().emit("end");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        for (PortDecl port : network.getInputPorts()) {
            // -- requested size
            emitter().emit("// -- int_%s_requested_size[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_requested_size[0+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_REQUESTED_SIZE_0)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_requested_size[0+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_requested_size[0+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();

            // -- size 0
            emitter().emit("// -- int_%s_size[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_size[0+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_size_0)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_size[0+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_size[0+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();

            // -- size 1
            emitter().emit("// -- int_%s_size[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_size[32+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_size_1)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_size[32+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_size[32+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();

            // -- buffer 0
            emitter().emit("// -- int_%s_buffer[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_buffer[0+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_buffer_0)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_buffer[0+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_buffer[0+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();

            // -- buffer 1
            emitter().emit("// -- int_%s_buffer[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_buffer[32+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_buffer_1)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_buffer[32+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_buffer[32+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();
        }

        for (PortDecl port : network.getOutputPorts()) {
            // -- size 0
            emitter().emit("// -- int_%s_size[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_size[0+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_size_0)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_size[0+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_size[0+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();

            // -- size 1
            emitter().emit("// -- int_%s_size[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_size[32+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_size_1)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_size[32+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_size[32+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();

            // -- buffer 0
            emitter().emit("// -- int_%s_buffer[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_buffer[0+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_buffer_0)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_buffer[0+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_buffer[0+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();

            // -- buffer 1
            emitter().emit("// -- int_%s_buffer[32-1:0]", port.getName());
            emitter().emit("always @(posedge aclk) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (areset)");
                emitter().emit("\tint_%s_buffer[32+:32] <= 32'd0;", port.getName());
                emitter().emit("else if (aclk_en) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == LP_ADDR_%s_buffer_1)", port.getName().toUpperCase());
                    emitter().emit("\tint_%s_buffer[32+:32] <= (wdata[0+:32] & wmask[0+:32]) | (int_%1$s_buffer[32+:32] & ~wmask[0+:32]);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();
        }


    }

}
