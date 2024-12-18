package ch.epfl.vlsc.hls.backend.verilog;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
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
        Path instanceTarget = PathUtils.getTargetCodeGenRtlTb(backend().context()).resolve("tb_" + identifier + ".v");

        // -- Get Entity
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        emitter().open(instanceTarget);

        getPreprocessor();

        emitter().emit("module tb_%s();", identifier);
        emitter().increaseIndentation();
        {
            clkAndReset(false);

            inputPortWiresAndReg(entity.getInputPorts(), false);

            outputPortWireAndRer(entity.getOutputPorts(), false);

            entity.getInputPorts().forEach(p -> queueWires(identifier, p, true));

            entity.getOutputPorts().forEach(p -> queueWires(identifier, p, false));

            getInitial(identifier, entity.getInputPorts(), entity.getOutputPorts(), true, false);

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

            if (!entity.getInputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Queues for input ports");
                entity.getInputPorts().forEach(p -> getQueue(identifier, p, true));
            }

            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Queues for output ports");
                entity.getOutputPorts().forEach(p -> getQueue(identifier, p, false));
            }

            getDut(instance, false);

            endOfSimulation(entity.getOutputPorts());
        }
        emitter().decreaseIndentation();
        emitter().emit("endmodule");


        emitter().close();
    }

    default void generateTestbenchSimple(Instance instance, boolean vivado2023) {
        String identifier = instance.getInstanceName();
        String vivado2023String = "";
        if (vivado2023) {
            vivado2023String = "_vivado2023";
        }
        Path instanceTarget = PathUtils.getTargetCodeGenRtlTb(backend().context()).resolve("tb_" + identifier +
                "_simple" + vivado2023String + ".v");

        // -- Get Entity
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        emitter().open(instanceTarget);

        getPreprocessor();

        emitter().emit("module tb_%s_simple" + vivado2023String + "();", identifier);
        emitter().increaseIndentation();
        {
            clkAndReset(vivado2023);

            inputPortWiresAndReg(entity.getInputPorts(), true);

            outputPortWireAndRer(entity.getOutputPorts(), true);

            entity.getInputPorts().forEach(p -> queueWires(identifier, p, true));

            entity.getOutputPorts().forEach(p -> queueWires(identifier, p, false));

            connectIOWire(identifier, entity.getInputPorts(), entity.getOutputPorts(), vivado2023);

            getInitial(identifier, entity.getInputPorts(), entity.getOutputPorts(), true, true);

            clockGeneration();


            /*if (!entity.getInputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Read from the files and write to the input fifos");
                entity.getInputPorts().forEach(this::readFromFileAndWrite);
            }*/

            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Read from output ports");
                entity.getOutputPorts().forEach(this::readFromOutputPort);
            }

            /*if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Compare with golden reference");
                entity.getOutputPorts().forEach(this::compareWithGoldenReference);
            }*/

            if (!entity.getInputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Queues for input ports");
                entity.getInputPorts().forEach(p -> getQueue(identifier, p, true));
            }

            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Queues for output ports");
                entity.getOutputPorts().forEach(p -> getQueue(identifier, p, false));
            }

            getDut(instance, vivado2023);
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
            clkAndReset(false);

            if (!network.getInputPorts().isEmpty()) {
                emitter().emit("// -- Input(s) Idle");
                emitter().emit("wire input_idle = 1'b0;");
            }


            inputPortWiresAndReg(network.getInputPorts(), false);

            outputPortWireAndRer(network.getOutputPorts(), false);

            getInitial(identifier, network.getInputPorts(), network.getOutputPorts(), false, false);

            clockGeneration();

            startPulseGenerator();

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

            getDut(network, false);
            endOfSimulation(network.getOutputPorts());
        }
        emitter().decreaseIndentation();
        emitter().emit("endmodule");

        emitter().close();

    }

    default void generateTestbenchSimple(Network network, boolean vivado2023) {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network file
        if (vivado2023) {
            emitter().open(PathUtils.getTargetCodeGenRtlTb(backend().context()).resolve("tb_" + identifier +
                    "_simple_vivado2023.v"));
        } else {
            emitter().open(PathUtils.getTargetCodeGenRtlTb(backend().context()).resolve("tb_" + identifier + "_simple" +
                    ".v"));
        }

        getPreprocessor();

        if (vivado2023) {
            emitter().emit("module tb_%s_simple_vivado2023();", identifier);
        } else {
            emitter().emit("module tb_%s_simple();", identifier);
        }
        emitter().increaseIndentation();
        {
            clkAndReset(false);

            if (!network.getInputPorts().isEmpty()) {
                emitter().emit("// -- Input(s) Idle");
                emitter().emit("wire input_idle = 1'b0;");
            }


            inputPortWiresAndReg(network.getInputPorts(), true);

            outputPortWireAndRer(network.getOutputPorts(), true);

            getInitial(identifier, network.getInputPorts(), network.getOutputPorts(), false, true);

            clockGeneration();

            startPulseGenerator();


            if (!network.getOutputPorts().isEmpty()) {
                emitter().emit("// ------------------------------------------------------------------------");
                emitter().emit("// -- Read from output ports");
                network.getOutputPorts().forEach(this::readFromOutputPort);
            }

            if (!vivado2023) {
                if (!network.getOutputPorts().isEmpty()) {
                    emitter().emit("// ------------------------------------------------------------------------");
                    emitter().emit("// -- Compare with golden reference");
                    network.getOutputPorts().forEach(this::compareWithGoldenReference);
                }
            }

            getDut(network, vivado2023);
        }
        emitter().decreaseIndentation();
        emitter().emit("endmodule");

        emitter().close();

    }

    default void connectIOWire(String name, ImmutableList<PortDecl> inputPorts, ImmutableList<PortDecl> outputPorts,
                               boolean vivado2023) {
        if (vivado2023) {
            String portSignals = "";
            for (PortDecl port : inputPorts) {
                String wireName = name.isEmpty() ? port.getName() : String.format("q_%s_%s", name, port.getName());
                portSignals = wireName + "_count, " + wireName + "_peek" + ", " + portSignals;
            }
            for (PortDecl port : outputPorts) {
                String wireName = name.isEmpty() ? port.getName() : String.format("q_%s_%s", name, port.getName());
                portSignals = wireName + "_count, " + wireName + "_size" + ", " + portSignals;
            }
            if (!portSignals.isEmpty()) {
                portSignals = portSignals.substring(0, portSignals.length() - 2);
            }
            int numBits = (inputPorts.size() + outputPorts.size()) * 64;
            emitter().emit("wire [%d:0] io_wire = {%s};", numBits - 1, portSignals);
        }
    }


    // ------------------------------------------------------------------------
    // -- Preprocessor

    default void getPreprocessor() {
        emitter().emit("`timescale 1 ns / 1 ps ");
        emitter().emit("`define NULL 0");
        emitter().emitNewLine();
    }


    // ------------------------------------------------------------------------
    // -- Registers and wires

    default void clkAndReset(boolean vivado2023) {
        emitter().emit("// -- CLK, reset_n and clock cycle");
        emitter().emit("parameter cycle = 10.0;");
        emitter().emit("reg clock;");
        emitter().emit("reg reset_n;");
        emitter().emitNewLine();

        emitter().emit("reg start;");
        emitter().emit("reg ap_start;");
        emitter().emit("reg check_idle;");
        emitter().emit("wire idle;");
        if (vivado2023) {
            emitter().emit("wire done;");
            emitter().emit("wire ready;");
            emitter().emit("wire [31:0] return;");
        }
        emitter().emitNewLine();
    }

    default void fileDataAndScan(PortDecl port) {
        emitter().emit("integer %s_data_file;", port.getName());
        emitter().emit("integer %s_scan_file;", port.getName());
        emitter().emitNewLine();
    }


    default void ioRegWires(PortDecl port, boolean isInput, boolean skipFileWriting) {
        Type type = backend().types().declaredPortType(port);
        int bitSize = backend().typeseval().sizeOfBits(type);
        boolean isSigned = false;

        if (type instanceof IntType) {
            isSigned = ((IntType) type).isSigned();
        }

        String name = port.getName();
        if (isInput) {
            emitter().emit("reg %s [%d:0] %s_din;", isSigned ? "signed" : "", bitSize - 1, name);
            if (!skipFileWriting) {
                emitter().emit("reg %s [%d:0] %s_din_tmp;", isSigned ? "signed" : "", bitSize - 1, name);
            }
            emitter().emit("reg %s_write;", name);
            emitter().emit("reg %s_idle = 1'b0;", name);
            emitter().emit("wire %s_full_n;", name);
            emitter().emitNewLine();
        } else {
            emitter().emit("wire %s [%d:0] %s_dout;", isSigned ? "signed" : "", bitSize - 1, name);
            emitter().emit("wire %s_empty_n;", name);
            emitter().emit("reg %s_read;", name);
            emitter().emitNewLine();
            if (!skipFileWriting) {
                emitter().emit("// -- Expected value, end of file and \"%s\" token counter", name);
                emitter().emit("reg %s [%d:0] %s_exp_value;", isSigned ? "signed" : "", bitSize - 1, name);
                emitter().emit("reg %s_end_of_file;", name);
                emitter().emit("reg [31:0] %s_token_counter;", name);
            }
            emitter().emitNewLine();
        }
    }

    default void inputPortWiresAndReg(List<PortDecl> ports, boolean skipFileWriting) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Input port registers & wires");
        emitter().emitNewLine();

        if (!skipFileWriting) {
            emitter().emit("// -- File Integers");
            ports.forEach(this::fileDataAndScan);
        }

        emitter().emit("// -- Input port registers, wires and state for reading");
        ports.forEach(p -> ioRegWires(p, true, skipFileWriting));
    }

    default void outputPortWireAndRer(List<PortDecl> ports, boolean skipFileWriting) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Output port registers & wires");
        emitter().emitNewLine();

        if (!skipFileWriting) {
            emitter().emit("// -- File Integers");
            ports.forEach(this::fileDataAndScan);
        }

        emitter().emit("// -- Output port registers, wires and state for reading");
        ports.forEach(p -> ioRegWires(p, false, skipFileWriting));
    }


    default void queueWires(String name, PortDecl port, boolean isInput) {
        String portName = port.getName();
        String queueName = "q_" + name + "_" + portName;
        Type type = backend().types().declaredPortType(port);
        int bitSize = backend().typeseval().sizeOfBits(type);

        emitter().emit("// -- Queue wires for port : %s", portName);
        if (isInput) {
            emitter().emit("wire %s_empty_n;", queueName);
            emitter().emit("wire %s_read;", queueName);
            emitter().emit("wire [%d:0] %s_dout;", bitSize - 1, queueName);
        } else {
            emitter().emit("wire %s_full_n;", queueName);
            emitter().emit("wire %s_write;", queueName);
            emitter().emit("wire [%d:0] %s_din;", bitSize - 1, queueName);
        }

        emitter().emit("wire [%d:0] %s_peek;", bitSize - 1, queueName);
        emitter().emit("wire [31:0] %s_count;", queueName);
        emitter().emit("wire [31:0] %s_size;", queueName);
        emitter().emitNewLine();

    }


    // ------------------------------------------------------------------------
    // -- Initial Block

    default void getInitial(String name, List<PortDecl> inputs, List<PortDecl> outputs, boolean isInstance,
                            boolean skipFileReading) {
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
            if (!skipFileReading) {
                emitter().emit("check_idle = 1'b0;");
            }
            emitter().emitNewLine();

            emitter().emit("// -- Initialize input port registers");
            for (PortDecl port : inputs) {
                String portName = port.getName();
                emitter().emit("%s_din = 1'b0;", portName);
                if (!skipFileReading) {
                    emitter().emit("%s_din_tmp = 1'b0;", portName);
                }
                emitter().emit("%s_write = 1'b0;", portName);
                emitter().emitNewLine();
            }

            emitter().emit("// -- Initialize output port registers");
            for (PortDecl port : outputs) {
                String portName = port.getName();
                emitter().emit("%s_read = 1'b0;", portName);
                if (!skipFileReading) {
                    emitter().emit("%s_end_of_file = 1'b0;", portName);
                    emitter().emit("%s_token_counter = 0;", portName);
                }
                emitter().emitNewLine();
            }

            if (!inputs.isEmpty() && !skipFileReading) {
                emitter().emit("// -- Open input vector data files");
                inputs.forEach(p -> initPortDataVector(name, p, isInstance));
            }

            if (!outputs.isEmpty() && !skipFileReading) {
                emitter().emit("// -- Open output vector data files");
                outputs.forEach(p -> initPortDataVector(name, p, isInstance));
            }

            emitter().emit("#55 reset_n = 1'b1;");
            emitter().emit("#10 start = 1'b1;");
            if (!skipFileReading) {
                emitter().emit("#20 check_idle = 1'b1;");
            }

            emitter().emitNewLine();
            if (skipFileReading) {
                emitter().emit("// -- Toggle input ports a few times to simulate input data.");
                toggleInputSignals(inputs);
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("end");
        emitter().emitNewLine();
    }

    default void toggleInputSignals(List<PortDecl> inputs) {

        int portValue = 0;
        if (!inputs.isEmpty()) {
            for (int i = 0; i < 5; i++) {
                emitter().emit("#10");
                for (PortDecl port : inputs) {
                    portValue += 10;
                    String name = port.getName();
                    emitter().emit("%s_din = %d;", name, portValue);
                    emitter().emit("%s_write = 1'b1;", name);
                }
                emitter().emit("#10");
                for (PortDecl port : inputs) {
                    String name = port.getName();
                    emitter().emit("%s_write = 1'b0;", name);
                }
                emitter().emitNewLine();
            }
        }
    }

    default void initPortDataVector(String name, PortDecl port, boolean isInstance) {
        String portName = port.getName();
        String fileName = portName;
        if (isInstance) {
            fileName = String.format("%s/%s", name, portName);
        }
        emitter().emit("%s_data_file = $fopen(\"../../../../../fifo-traces/%s.txt\" ,\"r\");", port.getName(),
                fileName);
        emitter().emit("if (%s_data_file == `NULL) begin", port.getName());
        emitter().increaseIndentation();
        {
            emitter().emit("$display(\"Error: File %s.txt does not exist !!!\");", fileName);
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
    // -- ap_start pulse generator
    default void startPulseGenerator() {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- ap_start pulse generator");
        emitter().emit("reg pulse_delay;");
        emitter().emitNewLine();

        emitter().emit("always @(posedge clock)");
        emitter().increaseIndentation();
        emitter().emit("pulse_delay <= start;");
        emitter().decreaseIndentation();
        emitter().emitNewLine();

        emitter().emit("always @(posedge clock)");
        emitter().increaseIndentation();
        emitter().emit("ap_start <= start && !pulse_delay;");
        emitter().decreaseIndentation();
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
                        emitter().emit("%s_idle <= 1'b0;", name);
                    }
                    emitter().decreaseIndentation();
                    emitter().emit("end else begin");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("%s_write <= 1'b0;", name);
                        emitter().emit("%s_idle <= 1'b1;", name);
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

                        emitter().emit("$display(\"Time: %s ns, Port %s: Error !!! Expected value does not match " +
                                "golden reference, Token Counter: %1$s\", $time, %2$s_token_counter);", "%0d", name);
                        emitter().emit("$display(\"\\tGot      : %s\", %s_dout);", "%0d", name);
                        emitter().emit("$display(\"\\tExpected : %s\", %s_exp_value);", "%0d", name);
                        emitter().emitNewLine();

                        emitter().emit("$finish;");
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

    // ------------------------------------------------------------------------
    // -- Queue

    default void getQueue(String name, PortDecl port, boolean isInput) {
        String portName = port.getName();
        String queueName = "q_" + name + "_" + portName;
        Type type = backend().types().declaredPortType(port);
        int bitSize = backend().typeseval().sizeOfBits(type);


        emitter().emit("// -- Queue FIFO for port : %s", portName);
        emitter().emit("FIFO #(");
        {
            emitter().increaseIndentation();
            emitter().emit(".MEM_STYLE(\"block\"),");
            emitter().emit(".DATA_WIDTH(%d),", bitSize);
            emitter().emit(".ADDR_WIDTH(9)");
            emitter().decreaseIndentation();
        }
        emitter().emit(") %s (", queueName);
        {
            emitter().increaseIndentation();
            emitter().emit(".clk(clock),");
            emitter().emit(".reset_n(reset_n),");

            String source;
            if (isInput) {
                source = portName;
            } else {
                source = queueName;
            }
            emitter().emit(".if_full_n(%s_full_n),", source);
            emitter().emit(".if_write(%s_write),", source);
            emitter().emit(".if_din(%s_din),", source);
            emitter().emitNewLine();

            String target;
            if (isInput) {
                target = queueName;
            } else {
                target = portName;
            }
            emitter().emit(".if_empty_n(%s_empty_n),", target);
            emitter().emit(".if_read(%s_read),", target);
            emitter().emit(".if_dout(%s_dout),", target);
            emitter().emitNewLine();

            emitter().emit(".peek(%s_peek),", queueName);
            emitter().emit(".count(%s_count),", queueName);
            emitter().emit(".size(%s_size)", queueName);
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
    }


    // ------------------------------------------------------------------------
    // -- Design under test

    default void getDut(Instance instance, boolean vivado2023) {
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
            entity.getInputPorts().forEach(p -> getDutIO(identifier, p, false, false, vivado2023));

            // -- Outputs
            entity.getOutputPorts().forEach(p -> getDutIO(identifier, p, true, false, vivado2023));

            // -- IO interface
            if (entity instanceof ActorMachine || entity instanceof CalActor) {
                entity.getInputPorts().forEach(p -> getIO(identifier, p, true, vivado2023));
                entity.getOutputPorts().forEach(p -> getIO(identifier, p, false, vivado2023));
                generateVivado2023IO(entity.getInputPorts(), entity.getOutputPorts(), vivado2023);
            }

            emitter().emit(".ap_clk(clock),");
            emitter().emit(".ap_rst_n(reset_n),");
            emitter().emit(".ap_start(start),");
            emitter().emit(".ap_idle(idle),");

            if (vivado2023) {
                emitter().emit(".ap_done(done),");
                emitter().emit(".ap_ready(ready),");
                emitter().emit(".ap_return(return)");
            }
        }
        emitter().decreaseIndentation();
        emitter().emit(");");
        emitter().emitNewLine();
    }

    default void generateVivado2023IO(ImmutableList<PortDecl> inputPorts, ImmutableList<PortDecl> outputPorts,
                                      boolean vivado2023) {
        if (vivado2023) {
            emitter().emit(".io(io_wire),");
        }
    }


    default void getDut(Network network, boolean vivado2023) {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Design under test");
        emitter().emitNewLine();

        emitter().emit("// -- Network input idle");
        if (!network.getInputPorts().isEmpty()) {
            emitter().emit("assign input_idle = %s;", String.join(" & ", network.getInputPorts()
                    .stream()
                    .map(p -> p.getName() + "_idle")
                    .collect(Collectors.toList())));
            emitter().emitNewLine();
        }
        if (vivado2023) {
            emitter().emit("%s_vivado2023 dut(", identifier);
        } else {
            emitter().emit("%s dut(", identifier);
        }
        emitter().increaseIndentation();
        {
            // -- Inputs
            network.getInputPorts().forEach(p -> getDutIO("", p, true, true, vivado2023));

            // -- Outputs
            network.getOutputPorts().forEach(p -> getDutIO("", p, false, true, vivado2023));

            emitter().emit(".ap_clk(clock),");
            emitter().emit(".ap_rst_n(reset_n),");
            emitter().emit(".ap_start(ap_start),");
            emitter().emit(".ap_idle(idle),");
            emitter().emit(".ap_done(done)");

        }
        emitter().decreaseIndentation();
        emitter().emit(");");
        emitter().emitNewLine();
    }

    default void getDutIO(String name, PortDecl port, boolean isInput, boolean isNetwork, boolean vivado2023) {
        Type type = backend().types().declaredPortType(port);
        String wireName = name.isEmpty() ? port.getName() : String.format("q_%s_%s", name, port.getName());
        String portName = name.isEmpty() ? port.getSafeName() : port.getName() + getPortExtension(type, vivado2023);
        if (vivado2023) {
            portName = name.isEmpty() ? backend().vnetwork().getPostHLSSynthesisPortName(port) :
                    backend().vnetwork().getPostHLSSynthesisPortName(port) + getPortExtension(type, vivado2023);
        }

        if (isInput) {
            emitter().emit(".%s_din(%s_din),", portName, wireName);
            emitter().emit(".%s_full_n(%s_full_n),", portName, wireName);
            emitter().emit(".%s_write(%s_write),", portName, wireName);

        } else {
            emitter().emit(".%s_dout(%s_dout),", portName, wireName);
            emitter().emit(".%s_empty_n(%s_empty_n),", portName, wireName);
            emitter().emit(".%s_read(%s_read),", portName, wireName);
        }
        if (isNetwork) {
            emitter().emit(".%s_fifo_count(), // unused", portName);
            emitter().emit(".%s_fifo_size(), // unused", portName);

            emitter().emit("// -- trigger constants");
            emitter().emit(".%s_sleep(1'b1),", portName);
            emitter().emit(".%s_sync_sleep(1'b1),", portName);
            emitter().emit(".%s_waited(1'b1),", portName);
        }
        emitter().emitNewLine();
    }

    default void getIO(String name, PortDecl port, boolean isInput, boolean vivado2023) {
        String wireName = name.isEmpty() ? port.getName() : String.format("q_%s_%s", name, port.getName());
        String portName = port.getName();
        if (!vivado2023) {
            if (isInput) {
                emitter().emit(".io_%s_peek(%s),", portName, String.format("%s_peek", wireName));
                emitter().emit(".io_%s_count(%s),", portName, String.format("%s_count", wireName));
            } else {
                emitter().emit(".io_%s_size(4096),", portName);
                emitter().emit(".io_%s_count(0),", portName);
            }
        }
        emitter().emitNewLine();
    }


    default String getPortExtension(Type type, boolean vivado2023) {
        // -- TODO : Add _V_V for type accuracy
        /*if (vivado2023) {
            return "_r";
        } else {
            return "_V";
        }*/
        return backend().vnetwork().getPortExtension(type, vivado2023);
    }


    // ------------------------------------------------------------------------
    // -- End of simulation

    default void endOfSimulation(List<PortDecl> outputs) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- End of simulation");

        emitter().emit("always @(posedge clock) begin");
        {
            emitter().increaseIndentation();

            if (outputs.isEmpty()) {
                emitter().emit("if (idle && check_idle) begin");
            } else {
                emitter().emit("if (idle && check_idle || %s) begin", String.join(" & ", outputs
                        .stream()
                        .map(p -> p.getName() + "_end_of_file")
                        .collect(Collectors.toList())));
            }
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