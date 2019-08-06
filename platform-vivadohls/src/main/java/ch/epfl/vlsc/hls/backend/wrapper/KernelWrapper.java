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

        // -- Wires and variables
        getWiresAndVariables(network);

        emitter().close();
    }

    // ------------------------------------------------------------------------
    // -- Parameters
    default void getParameters(Network network) {

        for (PortDecl port : network.getInputPorts()) {
            boolean lastElement = network.getOutputPorts().isEmpty() && (network.getInputPorts().size() - 1 == network.getInputPorts().indexOf(port));
            emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
            emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d%s", port.getName().toUpperCase(), AxiConstants.C_M_AXI_DATA_WIDTH, lastElement ? "" : ",");
            ;
        }
        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("parameter integer C_M_AXI_%s_ADDR_WIDTH = %d,", port.getName().toUpperCase(), AxiConstants.C_M_AXI_ADDR_WIDTH);
            emitter().emit("parameter integer C_M_AXI_%s_DATA_WIDTH = %d%s", port.getName().toUpperCase(), AxiConstants.C_M_AXI_DATA_WIDTH, network.getOutputPorts().size() - 1 == network.getOutputPorts().indexOf(port) ? "" : ",");
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
                backend().topkernel().getAxiMasterPorts(port);
            }
        }

        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                backend().topkernel().getAxiMasterPorts(port);
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
            emitter().emit("input  wire    [64 - 1 : 0]    %s_size,", port.getName());
            emitter().emit("input  wire    [64 - 1 : 0]    %s_buffer,", port.getName());
        }


        emitter().emit("input   wire    ap_start,");
        emitter().emit("output  wire    ap_idle,");
        emitter().emit("output  wire    ap_done");
    }

    // ------------------------------------------------------------------------
    // -- Wires and Variables
    default void getWiresAndVariables(Network network){

    }

}
