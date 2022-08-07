package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.hls.backend.ExternalMemory;
import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.Diagnostic;
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

            getParameters(network, "");

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


            // -- Input Stage(s)
            for (PortDecl port : network.getInputPorts()) {
                getIOStage(port, true);
            }

            // -- Network
            getNetwork(network);

            // -- Output Stage(s)
            for (PortDecl port : network.getOutputPorts()) {
                getIOStage(port, false);
            }

        }
        emitter().decreaseIndentation();
        emitter().emit("endmodule : %s_wrapper", identifier);
        emitter().emit("`default_nettype wire");

        emitter().close();
    }

    default String getAxiParameter(String name, String param, int value) {
        return "parameter integer C_M_AXI_" + name + "_" + param + " = " + value;
    }
    default ImmutableList<String> getPortParameters(PortDecl port) {
        Type type = backend().types().declaredPortType(port);
        int bitSize = backend().typeseval().sizeOfBits(type);
        String portName = port.getName().toUpperCase();
        ImmutableList.Builder<String> params  = ImmutableList.builder();
        if (bitSize > 512) {
            backend().context().getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Port " + portName +
                            " has unsupported axi bit width "  + bitSize)
            );
        }
        return ImmutableList.of(
                getAxiParameter(portName, "ID_WIDTH", 1),
                getAxiParameter(portName, "ADDR_WIDTH", AxiConstants.C_M_AXI_ADDR_WIDTH),
                getAxiParameter(portName, "DATA_WIDTH", AxiConstants.getAxiDataWidth(bitSize).orElse(512)),
                getAxiParameter(portName, "AWUSER_WIDTH", 1),
                getAxiParameter(portName, "ARUSER_WIDTH", 1),
                getAxiParameter(portName, "WUSER_WIDTH", 1),
                getAxiParameter(portName, "RUSER_WIDTH", 1),
                getAxiParameter(portName, "BUSER_WIDTH", 1),
                "parameter integer C_" + portName + "_USER_DW = " + bitSize

        );
    }
    // ------------------------------------------------------------------------
    // -- Parameters
    default void getParameters(Network network, String delim) {


        // -- external memory params


        backend().vnetwork().getExternalMemoryAxiParams(network,
                (network.getInputPorts().size() > 0 || network.getOutputPorts().size() > 0) ? "," : "");


        ImmutableList.Builder<String> params = ImmutableList.builder();
        ImmutableList<PortDecl> ports = ImmutableList.concat(network.getInputPorts(), network.getOutputPorts());
        for (PortDecl port : ports) {
            params.addAll(getPortParameters(port));
        }
        ImmutableList<String> parameters = params.build();


        for(String param : parameters) {
            boolean lastElement =  parameters.indexOf(param) == parameters.size() - 1;
            emitter().emit(param + (lastElement ? delim : ","));

        }
    }

    // ------------------------------------------------------------------------
    // -- Module port names
    default void getModulePortNames(Network network) {
        // -- System signals
        emitter().emit("input   wire    ap_clk,");
        emitter().emit("input   wire    ap_rst_n,");
        emitter().emit("input   wire    event_start,");

        // -- Network external memory ports ports
        if (!backend().externalMemory().getExternalMemories(network).isEmpty()) {
            for (Memories.InstanceVarDeclPair mem : backend().externalMemory().getExternalMemories(network)) {
                String memName = backend().externalMemory().namePair(mem);
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

        for (Memories.InstanceVarDeclPair mem : backend().externalMemory().getExternalMemories(network)) {
            String memName = backend().externalMemory().namePair(mem);
            emitter().emit("input  wire    [64 - 1 : 0]    %s_offset,", memName);
        }

        for (PortDecl port : ImmutableList.concat(network.getInputPorts(), network.getOutputPorts())) {
            emitter().emit("input  wire    [64 - 1 : 0] %s_data_buffer,", port.getName());
            emitter().emit("input  wire    [64 - 1 : 0] %s_meta_buffer,", port.getName());
            emitter().emit("input  wire    [32 - 1 : 0] %s_alloc_size,", port.getName());
            emitter().emit("input  wire    [32 - 1 : 0] %s_head,", port.getName());
            emitter().emit("input  wire    [32 - 1 : 0] %s_tail,", port.getName());

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
        emitter().emit("logic   ap_start_r = 1'b0;");
        emitter().emitNewLine();

        // -- Network I/O
        String moduleName = backend().task().getIdentifier().getLast().toString();
        emitter().emit("// -- Network I/O for %s module", moduleName);
        for (PortDecl port : network.getInputPorts()) {
            Type type = backend().types().declaredPortType(port);

            emitter().emit("wire    [C_%s_USER_DW - 1:0] %s_din;", port.getName().toUpperCase(),
                    port.getName());
            emitter().emit("wire    %s_full_n;", port.getName());
            emitter().emit("wire    %s_write;", port.getName());
            emitter().emit("wire    [31:0] %s_fifo_count;", port.getName());
            emitter().emit("wire    [31:0] %s_fifo_size;", port.getName());
            emitter().emit("wire    [63:0] %s_offset;", port.getSafeName());
        }

        for (PortDecl port : network.getOutputPorts()) {
            Type type = backend().types().declaredPortType(port);

            emitter().emit("wire    [C_%s_USER_DW - 1:0] %s_dout;", port.getName().toUpperCase(),
                    port.getName());
            emitter().emit("wire    %s_empty_n;", port.getName());
            emitter().emit("wire    %s_read;", port.getName());
            emitter().emit("wire    [31:0] %s_fifo_count;", port.getName());
            emitter().emit("wire    [31:0] %s_fifo_size;", port.getName());
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

        emitter().emit("// -- global trigger wires");
        emitter().emit("wire %s;", backend().vnetwork().getAllWaitedSignal());
        emitter().emit("wire %s;", backend().vnetwork().getAllSyncSleepSignal());
        emitter().emit("wire %s;", backend().vnetwork().getAllSleepSignal());
        emitter().emit("// -- local trigger wire");
        getPortsLocalTriggerWires(network.getInputPorts());
        getPortsLocalTriggerWires(network.getOutputPorts());


    }

    default void getPortsLocalTriggerWires(ImmutableList<PortDecl> ports) {
        for (PortDecl port : ports) {
            emitter().emit("wire    %s_sleep;", port.getSafeName());
            emitter().emit("wire    %s_sync_sleep;", port.getSafeName());
            emitter().emit("wire    %s_waited;", port.getSafeName());

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
        emitter().emit("logic    [1:0] ap_state = KERNEL_IDLE;");
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
                    String moduleName = backend().task().getIdentifier().getLast().toString();
                    emitter().increaseIndentation();
                    emitter().emit("KERNEL_IDLE	: ap_state <= (ap_start) ? KERNEL_START : KERNEL_IDLE;");
                    emitter().emit(
                            "KERNEL_START: ap_state <= (input_stage_idle && %s_ap_idle && output_stage_idle) ? KERNEL_DONE : KERNEL_START;", moduleName);
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
        String inputStageIdle = (network.getInputPorts().size() > 0) ?
            String.join(" & ", network.getInputPorts().stream()
                    .map(i -> i.getName() + "_input_stage_ap_idle")
                    .collect(Collectors.toList())) : "1'b1";
        String outputStageIdle = (network.getOutputPorts().size() > 0) ?
                String.join(" & ", network.getOutputPorts().stream()
                    .map(o -> o.getName() + "_output_stage_ap_idle")
                    .collect(Collectors.toList())) : "1'b1";

        emitter().emit("assign input_stage_idle = %s;", inputStageIdle);
        emitter().emitNewLine();

        emitter().emit("// -- output stage idle signal");
        emitter().emit("assign output_stage_idle = %s;", outputStageIdle);
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

    default void getIOStageParamBindings(PortDecl port) {
        emitter().emit(".C_M_AXI_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", port.getName().toUpperCase());
        emitter().emit(".C_M_AXI_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH ),", port.getName().toUpperCase());
        emitter().emit(".C_USER_DW( C_%s_USER_DW )", port.getName().toUpperCase());
    }

    default String getIOStageAxiSinglePortBinding(PortDecl port, String axiPort) {
        return String.format(".m_axi_%s(m_axi_%s_%1$s),", axiPort, port.getName());
    }
    default void getIOStageAxiPortBindings(PortDecl port) {
        ImmutableList<String> bindings = ImmutableList.of(
                "AWVALID",
                "AWREADY",
                "AWADDR",
                "AWID",
                "AWLEN",
                "AWSIZE",
                "AWBURST",
                "AWLOCK",
                "AWCACHE",
                "AWPROT",
                "AWQOS",
                "AWREGION",
                "AWUSER",
                "WVALID",
                "WREADY",
                "WDATA",
                "WSTRB",
                "WLAST",
                "WID",
                "WUSER",
                "ARVALID",
                "ARREADY",
                "ARADDR",
                "ARID",
                "ARLEN",
                "ARSIZE",
                "ARBURST",
                "ARLOCK",
                "ARCACHE",
                "ARPROT",
                "ARQOS",
                "ARREGION",
                "ARUSER",
                "RVALID",
                "RREADY",
                "RDATA",
                "RLAST",
                "RID",
                "RUSER",
                "RRESP",
                "BVALID",
                "BREADY",
                "BRESP",
                "BID",
                "BUSER"
        ).map(axiPort -> getIOStageAxiSinglePortBinding(port, axiPort));
        emitter().emit("// -- AXI master");
        for (String b : bindings) {
            emitter().emit("%s", b);
        }

    }

    default void getIOStageCommonBindings(PortDecl port) {

        getIOStageAxiPortBindings(port);
        emitter().emit("// --  ocl args");
        emitter().emit(".data_buffer(%s_data_buffer),", port.getName());
        emitter().emit(".meta_buffer(%s_meta_buffer),", port.getName());
        emitter().emit(".alloc_size(%s_alloc_size),", port.getName());
        emitter().emit(".head(%s_head),", port.getName());
        emitter().emit(".tail(%s_tail),", port.getName());
        // -- Trigger
        getTriggerGlobalBindings();
        getTriggerPortBindings(port);

    }


    default void getIOStage(PortDecl port, Boolean is_input) {
        emitter().emit("// -- %s stage for port : %s ", is_input ? "Input" : "Output", port.getName());
        emitter().emitNewLine();
        emitter().emit("assign %s_%s_ap_start = event_start;", port.getName(), is_input ? "input_stage" : "output_stage");
        emitter().emitNewLine();
        emitter().emitNewLine();
        String stage_type = is_input ? "input_stage" : "output_stage";
        emitter().emit("%s_%s_triggered #(", port.getName(), stage_type);
        {
            emitter().increaseIndentation();

            getIOStageParamBindings(port);

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emit("i_%s_%s(", port.getName(), stage_type);
        {
            emitter().increaseIndentation();

            emitter().emit("// -- AP control");
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n),");
            emitter().emit(".ap_start(%s_%s_ap_start),", port.getName(), stage_type);
            emitter().emit(".ap_done(%s_%s_ap_done),", port.getName(), stage_type);
            emitter().emit(".ap_idle(%s_%s_ap_idle),", port.getName(), stage_type);
            emitter().emit(".ap_ready(),");
            emitter().emit(".ap_return(%s_%s_ap_return),", port.getName(), stage_type);
            getIOStageCommonBindings(port);
            String portName = port.getName();
            // -- FIFO io
            if (is_input) {
                emitter().emit(".fifo_din(%s_din),", portName);
                emitter().emit(".fifo_full_n(%s_full_n),", portName);
                emitter().emit(".fifo_write(%s_write),", portName);

            } else {
                emitter().emit(".fifo_dout(%s_dout),", port.getName());
                emitter().emit(".fifo_empty_n(%s_empty_n),", port.getName());
                emitter().emit(".fifo_read(%s_read),", port.getName());

            }
            emitter().emit(".fifo_count(%s_fifo_count)", portName);



            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Network instantiation
    default void getNetwork(Network network) {
        String instanceName = backend().task().getIdentifier().getLast().toString();

        if (backend().externalMemory().getExternalMemories(network).isEmpty()) {
            emitter().emit("%s i_%1$s(", instanceName);
        } else {
            emitter().emit("%s #(", instanceName);
            {
                emitter().increaseIndentation();



                ImmutableList<Memories.InstanceVarDeclPair> mems =
                        backend().externalMemory().getExternalMemories(network);

                for (Memories.InstanceVarDeclPair mem: mems) {



                    ImmutableList<Memories.Pair<String, Integer>> params =
                            backend().externalMemory().getAxiParamPairs(mem);
                    for (Memories.Pair<String, Integer> param: params) {
                        boolean lastElement = (mems.indexOf(mem) == mems.size() - 1) &&
                                params.indexOf(param) == params.size() - 1;
                        emitter().emit(".%s(%1$s)%s", param.getFirst(), lastElement ? "" : ",");
                    }

                }

                emitter().decreaseIndentation();
            }
            emitter().emit(")");
            emitter().emit("i_%s (", instanceName);
        }
        {
            emitter().increaseIndentation();
            // -- External memories
            if (!backend().externalMemory().getExternalMemories(network).isEmpty()) {
                for (Memories.InstanceVarDeclPair mem : backend().externalMemory().getExternalMemories(network)) {
                    String memName = backend().externalMemory().namePair(mem);
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
                emitter().emit(".%s_sync_sleep(%1$s_sync_sleep),", port.getSafeName());
                emitter().emit(".%s_waited(%1$s_waited),", port.getSafeName());
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
                emitter().emit(".%s_sync_sleep(%1$s_sync_sleep),", port.getSafeName());
                emitter().emit(".%s_waited(%1$s_waited),", port.getSafeName());
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

        emitter().emit(".%s(%1$s),", backend().vnetwork().getAllSleepSignal());
        emitter().emit(".%s(%1$s),", backend().vnetwork().getAllSyncSleepSignal());
        emitter().emit(".%s(%1$s),", backend().vnetwork().getAllWaitedSignal());
    }

    default void getTriggerPortBindings(PortDecl port) {
        emitter().emit(".sleep(%s_sleep),", port.getSafeName());
        emitter().emit(".sync_sleep(%s_sync_sleep),", port.getSafeName());
        emitter().emit(".waited(%s_waited),", port.getSafeName());
    }


}
