package ch.epfl.vlsc.backend.verilog;

import ch.epfl.vlsc.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Module
public interface VerilogTestbench {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }


    default void generateTestbench(Instance instance) {
        String identifier = instance.getInstanceName();
        Path instanceTarget = PathUtils.getTargetCodeGenRtlTb(backend().context()).resolve("tb_" + backend().instaceQID(identifier, "_") + ".v");

        // -- Get Entity
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        emitter().open(instanceTarget);

        getPreprocessor();

        emitter().emit("module tb_%s();", identifier);
        emitter().increaseIndentation();
        {
            clkAndReset();

            inputPortWiresAndReg(entity.getInputPorts());

            outputPortWireAndRer(entity.getOutputPorts());

            getInitial(identifier, entity.getInputPorts(), entity.getOutputPorts());

            clockGeneration();

            if (!entity.getInputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Read from the files and write to the input fifos");
                entity.getInputPorts().forEach(this::readFromFileAndWrite);
            }

            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Read from output ports");
                entity.getOutputPorts().forEach(this::readFromOutputPort);
            }

            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Compare with golden reference");
                entity.getOutputPorts().forEach(this::compareWithGoldenReference);
            }

            getDut(instance);

            endOfSimulation(entity.getOutputPorts());
        }
        emitter().decreaseIndentation();
        emitter().emit("endmodule");


        emitter().close();
    }

    default void generateTestbench(Network network) {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenRtlTb(backend().context()).resolve("tb_" + identifier + ".v"));

        getPreprocessor();

        emitter().emit("module tb_%s();", identifier);
        emitter().increaseIndentation();
        {
            clkAndReset();

            inputPortWiresAndReg(network.getInputPorts());

            outputPortWireAndRer(network.getOutputPorts());

            getInitial(identifier, network.getInputPorts(), network.getOutputPorts());

            clockGeneration();

            if (!network.getInputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Read from the files and write to the input fifos");
                network.getInputPorts().forEach(this::readFromFileAndWrite);
            }

            if (!network.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Read from output ports");
                network.getOutputPorts().forEach(this::readFromOutputPort);
            }

            if (!network.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Compare with golden reference");
                network.getOutputPorts().forEach(this::compareWithGoldenReference);
            }

            getDut(network);

            endOfSimulation(network.getOutputPorts());
        }
        emitter().decreaseIndentation();
        emitter().emit("endmodule");

        emitter().close();

    }


    // ------------------------------------------------------------------------
    // -- Preprocessor

    default void getPreprocessor(){
        emitter().emit("`timescale 1 ns / 1 ps ");
        emitter().emit("`define NULL 0");
        emitter().emitNewLine();
    }


    // ------------------------------------------------------------------------
    // -- Registers and wires

    default void clkAndReset() {
        emitter().emit("// -- CLK, reset_n and clock cycle");
        emitter().emit("parameter cycle = 10.0;");
        emitter().emit("reg clock;");
        emitter().emit("reg reset_n;");
        emitter().emitNewLine();

        emitter().emit("reg start;");
        emitter().emit("wire idle;");
        emitter().emitNewLine();
    }

    default void fileDataAndScan(PortDecl port) {
        emitter().emit("integer %s_data_file;", port.getName());
        emitter().emit("integer %s_scan_file;", port.getName());
        emitter().emitNewLine();
    }


    default void ioRegWires(PortDecl port, boolean isInput) {
        Type type = backend().types().declaredPortType(port);
        int bitSize = TypeUtils.sizeOfBits(type);
        boolean isSigned = false;

        if (type instanceof IntType) {
            isSigned = ((IntType) type).isSigned();
        }

        String name = port.getName();
        if (isInput) {
            emitter().emit("reg %s [%d:0] %s_din;", isSigned ? "signed" : "", bitSize - 1, name);
            emitter().emit("reg %s [%d:0] %s_din_tmp;", isSigned ? "signed" : "", bitSize - 1, name);
            emitter().emit("reg %s_write;", name);
            emitter().emit("wire %s_full_n;", name);
            emitter().emitNewLine();
        } else {
            emitter().emit("wire %s [%d:0] %s_dout;", isSigned ? "signed" : "", bitSize - 1, name);
            emitter().emit("wire %s_empty_n;", name);
            emitter().emit("reg %s_read;", name);
            emitter().emitNewLine();
            emitter().emit("// -- Expected value, end of file and \"%s\" token counter", name);
            emitter().emit("reg %s [%d:0] %s_exp_value;", isSigned ? "signed" : "", bitSize - 1, name);
            emitter().emit("reg %s_end_of_file;", name);
            emitter().emit("reg [31:0] %s_token_counter;", name);
            emitter().emitNewLine();
        }
    }

    default void inputPortWiresAndReg(List<PortDecl> ports) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Input port registers & wires");
        emitter().emitNewLine();

        emitter().emit("// -- File Integers");
        ports.forEach(this::fileDataAndScan);

        emitter().emit("// -- Input port registers, wires and state for reading");
        ports.forEach(p -> ioRegWires(p, true));
    }

    default void outputPortWireAndRer(List<PortDecl> ports) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Output port registers & wires");
        emitter().emitNewLine();

        emitter().emit("// -- File Integers");
        ports.forEach(this::fileDataAndScan);

        emitter().emit("// -- Output port registers, wires and state for reading");
        ports.forEach(p -> ioRegWires(p, false));
    }


    // ------------------------------------------------------------------------
    // -- Initial Block

    default void getInitial(String name, List<PortDecl> inputs, List<PortDecl> outputs) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Initial block");
        emitter().emit("initial begin");
        emitter().increaseIndentation();
        {
            emitter().emit("$display(\"Testbench for entity : %s\");", name);
            emitter().emitNewLine();

            emitter().emit("// -- Initialize clock reset and start");
            emitter().emit("clock = 1'b0;");
            emitter().emit("reset_n = 1'b0;");
            emitter().emit("start = 1'b0;");
            emitter().emitNewLine();

            emitter().emit("// -- Initialize input port registers");
            for (PortDecl port : inputs) {
                String portName = port.getName();
                emitter().emit("%s_din = 1'b0;", portName);
                emitter().emit("%s_din_tmp = 1'b0;", portName);
                emitter().emit("%s_write = 1'b0;", portName);
                emitter().emitNewLine();
            }

            emitter().emit("// -- Initialize output port registers");
            for (PortDecl port : outputs) {
                String portName = port.getName();
                emitter().emit("%s_read = 1'b0;", portName);
                emitter().emit("%s_end_of_file = 1'b0;", portName);
                emitter().emit("%s_token_counter = 0;", portName);
                emitter().emitNewLine();
            }

            if (!inputs.isEmpty()) {
                emitter().emit("// -- Open input vector data files");
                inputs.forEach(this::initPortDataVector);
            }

            if (!outputs.isEmpty()) {
                emitter().emit("// -- Open output vector data files");
                outputs.forEach(this::initPortDataVector);
            }

            emitter().emit("#55 reset_n = 1'b1;");
            emitter().emit("#10 start = 1'b1;");
        }
        emitter().decreaseIndentation();
        emitter().emit("end");
        emitter().emitNewLine();
    }

    default void initPortDataVector(PortDecl port) {
        String name = port.getName();
        emitter().emit("%s_data_file = $fopen(\"../fifo-traces/%1$s.txt\" ,\"r\");", name);
        emitter().emit("if (%s_data_file == `NULL) begin", name);
        emitter().increaseIndentation();
        {
            emitter().emit("$display(\"Error: File %s.txt does not exist !!!\");", name);
            emitter().emit("$finish;");
        }
        emitter().decreaseIndentation();
        emitter().emit("end");
        emitter().emitNewLine();
    }


    // ------------------------------------------------------------------------
    // -- Clock generation

    default void clockGeneration() {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Clock generation");
        emitter().emit("always #(cycle / 2) clock = !clock;");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Read from file and write to the fifo

    default void readFromFileAndWrite(PortDecl port) {
        emitter().emit("always @(posedge clock) begin");
        emitter().increaseIndentation();
        {
            String name = port.getName();

            emitter().emit("if (reset_n == 1'b1) begin");
            emitter().increaseIndentation();
            {
                emitter().emit("if (%s_full_n) begin", name);
                emitter().increaseIndentation();
                {
                    emitter().emit("if (!$feof(%s_data_file)) begin", name);
                    emitter().increaseIndentation();
                    {
                        emitter().emit("%s_scan_file = $fscanf(%1$s_data_file, \"%s\\n\", %1$s_din_tmp);", name, "%d");
                        emitter().emit("%s_din <= %1$s_din_tmp;", name);
                        emitter().emit("%s_write <= 1'b1;", name);
                    }
                    emitter().decreaseIndentation();
                    emitter().emit("end else begin");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("%s_write <= 1'b0;", name);
                        emitter().decreaseIndentation();
                    }
                    emitter().emit("end");
                }
                emitter().decreaseIndentation();
                emitter().emit("end");
            }
            emitter().decreaseIndentation();
            emitter().emit("end");
        }
        emitter().decreaseIndentation();
        emitter().emit("end");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Compare from golden reference

    default void readFromOutputPort(PortDecl port) {
        emitter().emit("always @(posedge clock) begin");
        emitter().increaseIndentation();
        {
            String name = port.getName();

            emitter().emit("if (reset_n == 1'b1) begin");
            emitter().increaseIndentation();
            {
                emitter().emit("if (%s_empty_n) begin", name);
                emitter().increaseIndentation();
                {
                    emitter().emit("%s_read <= 1'b1;", name);
                }
                emitter().decreaseIndentation();

                emitter().emit("end else begin");
                {
                    emitter().increaseIndentation();
                    emitter().emit("%s_read <= 1'b0;", name);
                    emitter().decreaseIndentation();
                }

                emitter().emit("end");
            }
            emitter().decreaseIndentation();
            emitter().emit("end");
        }
        emitter().decreaseIndentation();
        emitter().emit("end");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Compare from golden reference

    default void compareWithGoldenReference(PortDecl port) {
        emitter().emit("always @(posedge clock) begin");
        {
            emitter().increaseIndentation();
            String name = port.getName();
            emitter().emit("if (!$feof(%s_data_file)) begin", name);
            emitter().increaseIndentation();
            {
                emitter().emit("if(%s_read & %1$s_empty_n) begin", name);
                {
                    emitter().increaseIndentation();

                    emitter().emit("%s_scan_file = $fscanf(%1$s_data_file, \"%s\\n\", %1$s_exp_value);", name, "%d");
                    emitter().emit("if (%s_dout != %1$s_exp_value) begin", name);
                    {
                        emitter().increaseIndentation();

                        emitter().emit("$display(\"Time: %s ns, Port %s: Error !!! Expected value does not match golden reference, Token Counter: %1$s\", $time, %2$s_token_counter);", "%0d", name);
                        emitter().emit("$display(\"\\tGot      : %s\", %s_dout);", "%0d", name);
                        emitter().emit("$display(\"\\tExpected : %s\", %s_exp_value);", "%0d", name);

                        emitter().decreaseIndentation();
                    }
                    emitter().emit("end else begin");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("$display(\"Time: %s ns, Port %s: Expected value matches golden reference, Token Counter: %1$s\", $time, %2$s_token_counter);", "%0d", name);
                        emitter().emit("$display(\"\\tGot      : %s\", %s_dout);", "%0d", name);
                        emitter().emit("$display(\"\\tExpected : %s\", %s_exp_value);", "%0d", name);

                        emitter().decreaseIndentation();
                    }
                    emitter().emit("end");
                    emitter().emit("%s_token_counter <= %1$s_token_counter + 1;", name);
                    emitter().decreaseIndentation();
                }
                emitter().emit("end");
            }
            emitter().decreaseIndentation();
            emitter().emit("end else begin");
            {
                emitter().increaseIndentation();
                emitter().emit("%s_end_of_file <= 1'b1;", name);
                emitter().decreaseIndentation();
                emitter().emit("end");
            }
            emitter().decreaseIndentation();

        }
        emitter().emit("end");
        emitter().emitNewLine();
    }

    default void getDut(Instance instance) {
        // -- Identifier
        String identifier = instance.getInstanceName();

        // -- Get Entity
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Design under test");

        emitter().emit("%s dut(", identifier);
        emitter().increaseIndentation();
        {
            // -- Inputs
            entity.getInputPorts().forEach(p -> getDutIO(identifier, p, true));

            // -- Outputs
            entity.getOutputPorts().forEach(p -> getDutIO(identifier, p, false));

            emitter().emit(".ap_clk(clock),");
            emitter().emit(".ap_rst_n(reset_n),");
            emitter().emit(".ap_start(start),");
            emitter().emit(".ap_idle(idle)");
        }
        emitter().decreaseIndentation();
        emitter().emit(");");
        emitter().emitNewLine();
    }


    default void getDut(Network network) {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Design under test");

        emitter().emit("%s dut(", identifier);
        emitter().increaseIndentation();
        {
            // -- Inputs
            network.getInputPorts().forEach(p -> getDutIO("", p, true));

            // -- Outputs
            network.getOutputPorts().forEach(p -> getDutIO("", p, false));

            emitter().emit(".ap_clk(clock),");
            emitter().emit(".ap_rst_n(reset_n),");
            emitter().emit(".ap_start(start),");
            emitter().emit(".ap_idle(idle)");
        }
        emitter().decreaseIndentation();
        emitter().emit(");");
        emitter().emitNewLine();
    }

    default void getDutIO(String name, PortDecl port, boolean isInput) {
        String portName = name.isEmpty() ? port.getName() : String.format("q_%s_%s", name, port.getName());
        if (isInput) {
            emitter().emit(".%s_din(%1$s_din),", portName);
            emitter().emit(".%s_full_n(%1$s_full_n),", portName);
            emitter().emit(".%s_write(%1$s_write),", portName);
        } else {
            emitter().emit(".%s_dout(%1$s_dout),", portName);
            emitter().emit(".%s_empty_n(%1$s_empty_n),", portName);
            emitter().emit(".%s_read(%1$s_read),", portName);
        }
        emitter().emitNewLine();
    }


    // ------------------------------------------------------------------------
    // -- End of simulation

    default void endOfSimulation(List<PortDecl> outputs) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- End of simulation");

        emitter().emit("always @(posedge clock) begin");
        {
            emitter().increaseIndentation();


            emitter().emit("if (%s) begin", String.join(" & ", outputs
                    .stream()
                    .map(p -> p.getName() + "_end_of_file")
                    .collect(Collectors.toList())));
            {
                emitter().increaseIndentation();

                emitter().emit("$display(\"Simulation has terminated !\");");
                emitter().emit("$finish;");

                emitter().decreaseIndentation();
            }
            emitter().emit("end");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();
    }

}