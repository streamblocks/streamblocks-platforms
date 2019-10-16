package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;

@Module
public interface AxiLiteControlKernels {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateAxiLiteContollers() {

        Network network = backend().task().getNetwork();

        getAxiLiteControlKernel("core", ImmutableList.of());
        getAxiLiteControlKernel("input", network.getInputPorts());
        getAxiLiteControlKernel("output", network.getOutputPorts());

    }

    default String getPipePrefix() {
        return "p_xcl_gv";
    }

    default String availableOrRequested(String kernelType) {
        return (kernelType == "input") ? "requested_size" : "available_size";
    }

    default String getAxiControlName(String kernelType) {
        return backend().kernel().getKernelName(kernelType) + "_control_s_axi";
    }
    default void getAxiLiteControlKernel(String kernelType, ImmutableList<PortDecl> kernelArgs) {

        // -- Network
        Network network = backend().task().getNetwork();
        // -- identifier
        String identifier = backend().task().getIdentifier().getLast().toString();
        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenKernel(backend().context(), kernelType)
                .resolve(getAxiControlName(kernelType) + ".v"));

        // -- Default net type
        emitter().emit("`default_nettype none");
        // -- Timescale
        emitter().emit("`timescale 1 ns / 1 ps");
        emitter().emitNewLine();

        // -- AXI4-Lite Control module
        emitter().emit("module %s #(", getAxiControlName(kernelType));
        // -- Parameters
        {
            emitter().increaseIndentation();

            getParameters();

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        // -- Ports I/O
        emitter().emit("(");
        {
            emitter().increaseIndentation();

            getAxiSlaveIO(kernelArgs, kernelType);

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();

        // -- Local parameters
        getLocalParameters(kernelArgs, kernelType);

        // -- Wires and variables
        getWiresAndVariables(kernelArgs, kernelType);

        // -- RTL Body
        emitter().emitClikeBlockComment("Begin RTL Body");
        emitter().emitNewLine();

        // -- AXI Write FSM
        getAxiWriteFSM();

        // -- AXI Read FSM
        getAxiReadFSM(kernelArgs, kernelType);

        // -- Register Logic
        getRegisterLogic(kernelArgs, kernelType);

        emitter().emitNewLine();
        emitter().emit("endmodule");
        emitter().emit("`default_nettype wire");
        emitter().close();

    }

    default void getParameters() {
        // -- AXI4-Lite Address and data width
        emitter().emit("parameter integer C_S_AXI_ADDR_WIDTH = %d,", getAddressBitWidth(backend().task().getNetwork()));
        emitter().emit("parameter integer C_S_AXI_DATA_WIDTH = %s", AxiConstants.C_S_AXI_CONTROL_DATA_WIDTH);
    }

    default void getAxiSlaveIO(ImmutableList<PortDecl> kernelArgs, String kernelType) {
        // -- System signals

        emitter().emit("// -- axi4 lite slave signals");
        emitter().emit("input  wire                          ACLK,");
        emitter().emit("input  wire                          ARESET,");
        emitter().emit("input  wire                          ACLK_EN,");
        emitter().emit("input  wire [C_S_AXI_ADDR_WIDTH-1:0] AWADDR,");
        emitter().emit("input  wire                          AWVALID,");
        emitter().emit("output wire                          AWREADY,");
        emitter().emit("input  wire [C_S_AXI_DATA_WIDTH-1:0] WDATA,");
        emitter().emit("input  wire [C_S_AXI_DATA_WIDTH/8-1:0] WSTRB,");
        emitter().emit("input  wire                          WVALID,");
        emitter().emit("output wire                          WREADY,");
        emitter().emit("output wire [1:0]                    BRESP,");
        emitter().emit("output wire                          BVALID,");
        emitter().emit("input  wire                          BREADY,");
        emitter().emit("input  wire [C_S_AXI_ADDR_WIDTH-1:0] ARADDR,");
        emitter().emit("input  wire                          ARVALID,");
        emitter().emit("output wire                          ARREADY,");
        emitter().emit("output wire [C_S_AXI_DATA_WIDTH-1:0] RDATA,");
        emitter().emit("output wire [1:0]                    RRESP,");
        emitter().emit("output wire                          RVALID,");
        emitter().emit("input  wire                          RREADY,");

        emitter().emit("// user kernel signals");
        for (PortDecl port : kernelArgs) {
            emitter().emit("output  wire    [31 : 0]    %s_%s,", port.getName(), availableOrRequested(kernelType));
            emitter().emit("output  wire    [63 : 0]    %s_size,", port.getName());
            emitter().emit("output  wire    [63 : 0]    %s_buffer,", port.getName());
            emitter().emit("output  wire    [63 : 0]    p_xcl_gv_%s,", backend().kernel().getPipeName(port));
        }

        emitter().emit("// Ap signals");
        emitter().emit("output  wire    ap_start,");
        emitter().emit("input   wire    ap_done,");
        emitter().emit("input   wire    ap_ready,");
        emitter().emit("input   wire    ap_idle,");
        emitter().emit("output  wire    event_start,");
        emitter().emit("output  wire    interrupt");

    }

    default void getLocalParameters(ImmutableList<PortDecl> kernelArgs, String kernelType) {
        emitter().emitClikeBlockComment("Local Parameters");
        emitter().emitNewLine();
        emitter().emit("localparam");
        emitter().increaseIndentation();
        int addressWidth = getAddressBitWidth(backend().task().getNetwork());
        int value = 0;
        emitter().emit("ADDR_AP_CTRL = %d'h%s,", addressWidth, String.format("%x", value));
        value += 4;
        emitter().emit("ADDR_GIE = %d'h%s,", addressWidth, String.format("%x", value));
        value += 4;
        emitter().emit("ADDR_IER = %d'h%s,", addressWidth, String.format("%x", value));
        value += 4;
        emitter().emit("ADDR_ISR = %d'h%s,", addressWidth, String.format("%x", value));
        value += 4;

        for (PortDecl port : kernelArgs) {
            emitter().emit("ADDR_%s_%s_DATA_0 = %d'h%s,", port.getName().toUpperCase(),
                    availableOrRequested(kernelType).toUpperCase(), addressWidth, String.format("%x", value));
            value += 4;
            emitter().emit("ADDR_%s_%s_CTRL = %d'h%s,", port.getName().toUpperCase(),
                    availableOrRequested(kernelType).toUpperCase(), addressWidth, String.format("%x", value));
            value += 4;
        }

        for (PortDecl port : kernelArgs) {
            emitter().emit("ADDR_%s_SIZE_DATA_0 = %d'h%s,", port.getName().toUpperCase(), addressWidth,
                    String.format("%x", value));
            value += 4;
            emitter().emit("ADDR_%s_SIZE_DATA_1 = %d'h%s,", port.getName().toUpperCase(), addressWidth,
                    String.format("%x", value));
            value += 4;
            emitter().emit("ADDR_%s_SIZE_CTRL = %d'h%s,", port.getName().toUpperCase(), addressWidth,
                    String.format("%x", value));
            value += 4;
            emitter().emit("ADDR_%s_BUFFER_DATA_0 = %d'h%s,", port.getName().toUpperCase(), addressWidth,
                    String.format("%x", value));
            value += 4;
            emitter().emit("ADDR_%s_BUFFER_DATA_1 = %d'h%s,", port.getName().toUpperCase(), addressWidth,
                    String.format("%x", value));
            value += 4;
            emitter().emit("ADDR_%s_BUFFER_CTRL = %d'h%s,", port.getName().toUpperCase(), addressWidth,
                    String.format("%x", value));
            value += 4;
        }

        for (PortDecl port : kernelArgs) {
            emitter().emit("ADDR_%s_%s_DATA_0 = %d'h%x,", getPipePrefix().toUpperCase(),
                    backend().kernel().getPipeName(port).toUpperCase(), addressWidth, value);
            value += 4;
            emitter().emit("ADDR_%s_%s_DATA_1 = %d'h%x,", getPipePrefix().toUpperCase(),
                    backend().kernel().getPipeName(port).toUpperCase(), addressWidth, value);
            value += 4;
            emitter().emit("ADDR_%s_%s_CTRL = %d'h%x,", getPipePrefix().toUpperCase(),
                    backend().kernel().getPipeName(port).toUpperCase(), addressWidth, value);
            value += 4;
        }

        emitter().emit("WRIDLE = 2'd0,");
        emitter().emit("WRDATA = 2'd1,");
        emitter().emit("WRRESP = 2'd2,");
        emitter().emit("WRRESET = 2'd3,");
        emitter().emit("RDIDLE = 2'd0,");
        emitter().emit("RDDATA = 2'd1,");
        emitter().emit("RDRESET = 2'd2,");
        emitter().emit("ADDR_BITS = %d;", addressWidth);
        emitter().decreaseIndentation();

        emitter().emitNewLine();
    }

    default void getWiresAndVariables(ImmutableList<PortDecl> kernelArgs, String kernelType) {
        emitter().emitClikeBlockComment("Wires and Variables");
        emitter().emitNewLine();

        emitter().emit("reg  [1:0] wstate = WRRESET;");
        emitter().emit("reg  [1:0] wnext;");
        emitter().emit("reg  [ADDR_BITS-1:0] waddr;");
        emitter().emit("wire [31:0] wmask;");
        emitter().emit("wire aw_hs;");
        emitter().emit("wire w_hs;");
        emitter().emit("reg  [1:0] rstate = RDRESET;");
        emitter().emit("reg  [1:0] rnext;");
        emitter().emit("reg  [31:0] rdata;");
        emitter().emit("wire ar_hs;");
        emitter().emit("wire [ADDR_BITS-1:0] raddr;");
        emitter().emit("// -- internal registers");
        emitter().emit("reg int_event_start = 1'b0;");
        emitter().emit("reg int_ap_idle;");
        emitter().emit("reg int_ap_ready;");
        emitter().emit("reg int_ap_done = 1'b0;");
        emitter().emit("reg int_ap_start = 1'b0;");
        emitter().emit("reg int_auto_restart = 1'b0;");
        emitter().emit("reg int_gie = 1'b0;");
        emitter().emit("reg [1:0] int_ier = 2'b0;");
        emitter().emit("reg [1:0] int_isr = 2'b0;");

        emitter().emitNewLine();

        emitter().emit("// -- Internal Registers for addresses for I/O");
        for (PortDecl port : kernelArgs) {
            emitter().emit("reg [31 : 0]    int_%s_%s = 32'd0;", port.getName(), availableOrRequested(kernelType));
            emitter().emit("reg [63 : 0]    int_%s_size = 64'd0;", port.getName());
            emitter().emit("reg [63 : 0]    int_%s_buffer = 64'd0;", port.getName());
            emitter().emit("reg [63 : 0]    int_%s_%s;", getPipePrefix(), backend().kernel().getPipeName(port));
        }
        emitter().emitNewLine();
    }

    default void getAxiWriteFSM() {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- AXI Write FSM");
        emitter().emit("assign AWREADY = (wstate == WRIDLE);");
        emitter().emit("assign WREADY  = (wstate == WRDATA);");
        emitter().emit("assign BRESP   = 2'b00;  // -- OKAY");
        emitter().emit("assign BVALID  = (wstate == WRRESP);");
        emitter().emit("assign wmask   = { {8{WSTRB[3]}}, {8{WSTRB[2]}}, {8{WSTRB[1]}}, {8{WSTRB[0]}} };");
        emitter().emit("assign aw_hs   = AWVALID & AWREADY;");
        emitter().emit("assign w_hs    = WVALID & WREADY;");
        emitter().emitNewLine();

        // -- Write state
        emitter().emit("// -- Write state");
        emitter().emit("always @(posedge ACLK) begin");
        emitter().increaseIndentation();
        {
            emitter().emit("if (ARESET)");
            {
                emitter().increaseIndentation();

                emitter().emit("wstate <= WRRESET;");

                emitter().decreaseIndentation();
            }
            emitter().emit(" else if (ACLK_EN)");
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

                emitter().emit("WRIDLE:");
                {
                    emitter().increaseIndentation();
                    emitter().emit("if (AWVALID)");
                    emitter().emit("\twnext = WRDATA;");
                    emitter().emit("else");
                    emitter().emit("\twnext = WRIDLE;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("WRDATA:");
                {
                    emitter().increaseIndentation();
                    emitter().emit("if (WVALID)");
                    emitter().emit("\twnext = WRRESP;");
                    emitter().emit("else");
                    emitter().emit("\twnext = WRDATA;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("WRRESP:");
                {
                    emitter().increaseIndentation();
                    emitter().emit("if (BREADY)");
                    emitter().emit("\twnext = WRIDLE;");
                    emitter().emit("else");
                    emitter().emit("\twnext = WRRESP;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("default:");
                emitter().emit("\twnext = WRIDLE;");

                emitter().decreaseIndentation();
            }
            emitter().emit("endcase");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        emitter().emit("// -- Write address");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (aw_hs)");
                emitter().emit("\twaddr <= AWADDR[ADDR_BITS-1:0];");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();
    }

    default void getAxiReadFSM(ImmutableList<PortDecl> kernelArgs, String kernelType) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- AXI Read FSM");

        emitter().emit("assign  ARREADY = (rstate == RDIDLE);");
        emitter().emit("assign  RDATA   = rdata;");
        emitter().emit("assign  RRESP   = 2'b00;  // OKAY");
        emitter().emit("assign  RVALID  = (rstate == RDDATA);");
        emitter().emit("assign  ar_hs   = ARVALID & ARREADY;");
        emitter().emit("assign  raddr   = ARADDR[ADDR_BITS-1:0];");
        emitter().emitNewLine();

        emitter().emit("// -- Read state");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\trstate <= RDRESET;");
            emitter().emit("else if (ACLK_EN)");
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
                emitter().emit("RDIDLE:");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (ARVALID)");
                    emitter().emit("\trnext = RDDATA;");
                    emitter().emit("else");
                    emitter().emit("\trnext = RDIDLE;");

                    emitter().decreaseIndentation();
                }

                // -- SM_RDDATA
                emitter().emit("RDDATA:");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (RREADY & RVALID)");
                    emitter().emit("\trnext = RDIDLE;");
                    emitter().emit("else");
                    emitter().emit("\trnext = RDDATA;");

                    emitter().decreaseIndentation();
                }

                // -- default
                emitter().emit("default:");
                emitter().emit("\t rnext = RDIDLE;");

                emitter().decreaseIndentation();
            }
            emitter().emit("endcase");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        emitter().emit("// -- Read data");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (ar_hs) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("rdata <= 1'b0;");
                    emitter().emit("case (raddr)");
                    {
                        emitter().increaseIndentation();

                        // -- ADDR_AP_CTRL
                        emitter().emit("ADDR_AP_CTRL: begin");
                        {
                            emitter().increaseIndentation();

                            emitter().emit("rdata[0] <= int_ap_start;");
                            emitter().emit("rdata[1] <= int_ap_done;");
                            emitter().emit("rdata[2] <= int_ap_idle;");
                            emitter().emit("rdata[3] <= int_ap_ready;");
                            emitter().emit("rdata[7] <= int_auto_restart;");

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("end");

                        // -- ADDR_GIE
                        emitter().emit("ADDR_GIE: begin");
                        {
                            emitter().increaseIndentation();

                            emitter().emit("rdata <= int_gie;");

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("end");

                        // -- ADDR_IER
                        emitter().emit("ADDR_IER: begin");
                        {
                            emitter().increaseIndentation();

                            emitter().emit("rdata <= int_ier;");

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("end");

                        // -- ADDR_ISR
                        emitter().emit("ADDR_ISR: begin");
                        {
                            emitter().increaseIndentation();

                            emitter().emit("rdata <= int_isr;");

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("end");

                        for (PortDecl port : kernelArgs) {
                            emitter().emit("ADDR_%s_REQUESTED_SIZE_DATA_0: begin", port.getName().toUpperCase());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata <= int_%s_%s[31:0];", port.getName(),
                                        availableOrRequested(kernelType));

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("ADDR_%s_SIZE_DATA_0: begin", port.getName().toUpperCase());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata<= int_%s_size[31:0];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("ADDR_%s_SIZE_DATA_1: begin", port.getName().toUpperCase());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata<= int_%s_size[63:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("ADDR_%s_BUFFER_DATA_0: begin", port.getName().toUpperCase());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata <= int_%s_buffer[31:0];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("ADDR_%s_BUFFER_DATA_1: begin", port.getName().toUpperCase());
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata <= int_%s_buffer[63:32];", port.getName());

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("ADDR_%S_%S_DATA_0: begin", getPipePrefix().toUpperCase(),
                                    backend().kernel().getPipeName(port));
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata <= int_%s_%s[31:0];", getPipePrefix(),
                                        backend().kernel().getPipeName(port));

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");

                            emitter().emit("ADDR_%S_%S_DATA_1: begin", getPipePrefix().toUpperCase(),
                                    backend().kernel().getPipeName(port));
                            {
                                emitter().increaseIndentation();

                                emitter().emit("rdata <= int_%s_%s[63:32];", getPipePrefix(),
                                        backend().kernel().getPipeName(port));

                                emitter().decreaseIndentation();
                            }
                            emitter().emit("end");
                        }

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

    default void getRegisterLogic(ImmutableList<PortDecl> kernelArgs, String kernelType) {
        emitter().emitClikeBlockComment("Register Logic");
        emitter().emit("assign interrupt    = int_gie & (|int_isr);");
        emitter().emit("assign event_start  = int_event_start;");
        emitter().emit("assign ap_start     = int_ap_start;");
        for (PortDecl port : kernelArgs) {
            emitter().emit("assign %s_%s = int_%1$s_%2$s;", port.getName(), availableOrRequested(kernelType));
            emitter().emit("assign %s_size = int_%1$s_size;", port.getName());
            emitter().emit("assign %s_buffer = int_%1$s_buffer;", port.getName());
            emitter().emit("assign %s_%s = int_%1$s_%2$s;", getPipePrefix(), backend().kernel().getPipeName(port));
        }

        emitter().emitNewLine();

        // int_event_start
        emitter().emit("// -- int_event_start");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_event_start <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (w_hs && waddr == ADDR_AP_CTRL && WSTRB[0] && WDATA[0])");
                emitter().emit("\tint_event_start <= 1'b1;");
                emitter().emit("else");
                emitter().emit("\tint_event_start <= 1'b0; // -- self clear");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_ap_start
        emitter().emit("// -- int_ap_start");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_ap_start <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (w_hs && waddr == ADDR_AP_CTRL && WSTRB[0] && WDATA[0])");
                emitter().emit("\tint_ap_start <= 1'b1;");
                emitter().emit("else if (ap_ready)");
                emitter().emit("\tint_ap_start <= int_auto_restart; // clear on handshake/auto restart");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_ap_done
        emitter().emit("// -- int_ap_done");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_ap_done <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (ap_done)");
                emitter().emit("\tint_ap_done <= 1'b1;");
                emitter().emit("else if (ar_hs && raddr == ADDR_AP_CTRL)");
                emitter().emit("\tint_ap_done <= 1'b0; // -- clear on read");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_ap_idle
        emitter().emit("// -- int_ap_idle");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_ap_idle <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            emitter().emit("\tint_ap_idle <= ap_idle;");
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");

        // -- int_ap_ready
        emitter().emit("// -- int_ap_ready");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_ap_ready <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            emitter().emit("\tint_ap_ready <= ap_ready;");
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");

        // -- int_auto_restart
        emitter().emit("// -- int_auto_restart");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_auto_restart <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (w_hs && waddr == ADDR_AP_CTRL && WSTRB[0])");
                emitter().emit("\tint_auto_restart <=  WDATA[7];");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");

        // -- int_gie
        emitter().emit("// -- int_gie");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_gie <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();
                emitter().emit("if (w_hs && waddr == ADDR_GIE && WSTRB[0])");
                emitter().emit("\tint_gie <= WDATA[0];");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_ier
        emitter().emit("// -- int_ier");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_ier <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();
                emitter().emit("if (w_hs && waddr == ADDR_IER && WSTRB[0])");
                emitter().emit("\t int_ier <= WDATA[1:0];");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_isr[0]
        emitter().emit("// -- int_isr[0]");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_isr[0] <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();
                emitter().emit("if (int_ier[0] & ap_done)");
                emitter().emit("\tint_isr[0] <= 1'b1;");
                emitter().emit("else if (w_hs && waddr == ADDR_ISR && WSTRB[0])");
                emitter().emit("\tint_isr[0] <= int_isr[0] ^ WDATA[0];");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        // -- int_isr[1]
        emitter().emit("// -- int_isr");
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_isr[1] <= 1'b0;");
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();
                emitter().emit("if (int_ier[1] & ap_ready)");
                emitter().emit("\tint_isr[1] <= 1'b1;");
                emitter().emit("else if (w_hs && waddr == ADDR_ISR && WSTRB[0])");
                emitter().emit("\tint_isr[1] <= int_isr[1] ^ WDATA[1];");
                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        for (PortDecl port : kernelArgs) {
            // -- requested size
            getReg32Bit(port.getName(), availableOrRequested(kernelType));
        }

        for (PortDecl port : kernelArgs) {
            // -- size
            getReg64Bit(port.getName(), "size");

            // -- buffer
            getReg64Bit(port.getName(), "buffer");

            // -- pipe
            getReg64Bit(getPipePrefix(), backend().kernel().getPipeName(port));
        }

    }

    default void getReg32Bit(String port, String name) {
        emitter().emit("// -- int_%s_%s[31:0]", port, name);
        emitter().emit("always @(posedge ACLK) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if (ARESET)");
            emitter().emit("\tint_%s_%s[31:0] <= 0;", port, name);
            emitter().emit("else if (ACLK_EN) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (w_hs && waddr == ADDR_%s_%s_DATA_0)", port.toUpperCase(), name.toUpperCase());
                emitter().emit("\tint_%s_%s[31:0] <= (WDATA[31:0] & wmask) | (int_%1$s_%2$s[31:0] & ~wmask);", port,
                        name);

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();
    }

    default void getReg64Bit(String port, String name) {

        int bits = 0;
        for (int i = 0; i < 2; i++) {
            emitter().emit("// -- int_%s_%s[%d:%d]", port, name, 31 + bits, bits);
            emitter().emit("always @(posedge ACLK) begin");
            {
                emitter().increaseIndentation();

                emitter().emit("if (ARESET)");
                emitter().emit("\tint_%s_%s[%d:%d] <= 0;", port, name, 31 + bits, bits);
                emitter().emit("else if (ACLK_EN) begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (w_hs && waddr == ADDR_%s_%s_DATA_%d)", port.toUpperCase(), name.toUpperCase(),
                            i);
                    emitter().emit(
                            "\tint_%1$s_%2$s[%3$d:%4$d] <= (WDATA[31:0] & wmask) | (int_%1$s_%2$s[%3$d:%4$d] & ~wmask);",
                            port, name, 31 + bits, bits);

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");
            emitter().emitNewLine();
            bits += 32;
        }
    }

    // -- Helper Methods

    default int getAddressBitWidth(Network network) {
        int value = 16 + network.getInputPorts().size() * 8 + network.getOutputPorts().size() * 8
                + network.getInputPorts().size() * 24 + network.getOutputPorts().size() * 24;
        return MathUtils.countBit(value);
    }

}
