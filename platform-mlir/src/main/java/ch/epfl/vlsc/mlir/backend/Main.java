package ch.epfl.vlsc.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.nio.file.Path;
import java.util.*;

/**
 * Generate a top module for the DFG MLIR dialect.
 * <p>
 * Here is an example of what we need to generate:
 * <p>
 * func.func @top(%in1: i32, %in2: i32, %in3: i32) -> i32
 * {
 * %q1_in, %q1_out = dfg.channel(4) : i32
 * %q2_in, %q2_out = dfg.channel(4) : i32
 * %q3_in, %q3_out = dfg.channel(4) : i32
 * %q4_in, %q4_out = dfg.channel(4) : i32
 * %q5_in, %q5_out = dfg.channel(4) : i32
 * <p>
 * dfg.push(%in1) %q1_in : i32
 * dfg.push(%in2) %q2_in : i32
 * dfg.push(%in3) %q3_in : i32
 * <p>
 * <p>
 * dfg.instantiate @adder inputs(%q1_out, %q2_out) outputs(%q4_in) : (i32, i32) -> i32
 * dfg.instantiate @multiplier inputs(%q4_out, %q3_out) outputs(%q5_in) : (i32, i32) -> i32
 * <p>
 * %0 = dfg.pull %q5_out : i32
 * func.return %0 : i32
 * }
 */
@Module
public interface Main {

    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Emitter emitter() {
        return backend().emitter();
    }

    default ExpressionEvaluator evaluator() {
        return backend().expressionEval();
    }

    default void main() {
        Path mainTarget = PathUtils.getTargetCodeGen(backend().context()).resolve("main.mlir");
        emitter().open(mainTarget);
        initNetwork();
        emitter().close();
    }

    default void initNetwork() {
        Network network = backend().task().getNetwork();

        Map<Connection.End, List<Connection.End>> srcToTgt = new HashMap<>();
        List<Connection> connections = network.getConnections();

        // 1. Generate all connections, accounting for src ports going to multiple target ports
        // This is used later in the code
        for (Connection connection : connections) {
            Connection.End src = connection.getSource();
            Connection.End tgt = connection.getTarget();
            srcToTgt.computeIfAbsent(src, x -> new ArrayList<>())
                    .add(tgt);
        }

        System.out.println(srcToTgt);

        // 2. Generate the list of input operands for the %top operation:
        //  In the example: "func.func @top(%in1: i32, %in2: i32, %in3: i32)", %in3: i32, is one input operand
        //  with both the name and return type specified. We generate the list op operands in this format
        String inArgs = "";
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                // We need the name of the port as well as the token type. We can get the token type from
                // channelUtils, but we need a Connection.End object.
                // This is present in srctoTgt map, but to search this is unecessary if we can just regenerate the
                // object.
                Connection.End end = new Connection.End(Optional.empty(), port.getName());
                String tokenType = backend().typeseval().type(backend().channelsutils().sourceEndType(end)).toString();
                inArgs = "%" + inArgs + port.getName() + ": " + tokenType + ", ";
            }
            inArgs = inArgs.substring(0, inArgs.length() - 2);
        }

        // 3. The return  from the top needs to be specified in two places:
        //      1. The list of return types in the @top function: @top(...) -> i32, i32  //(i32, i32 are the return
        //      types)
        //      2. In the func.return operation as "func.return %0, %1 : i32, i32" // %0 %1 are the operands to return
        //      with i32, i32 being their type.
        // We generate the lists of types and operands separately, to be combined later as needed.
        String outOperands = "";
        String outTypes = "";
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                Connection.End end = new Connection.End(Optional.empty(), port.getName());
                String tokenType = backend().typeseval().type(backend().channelsutils().targetEndType(end)).toString();
                outTypes = outTypes + tokenType + ", ";
                outOperands = outOperands + "%" + port.getName() + ", ";
            }
            outOperands = outOperands.substring(0, outOperands.length() - 2);
            outTypes = outTypes.substring(0, outTypes.length() - 2);
        }

        // 4. We now finally have everything we need for the func operation, so lets generate it
        // 4.1 Generate the first line of the operation
        emitter().emit("func.func @top(%s) -> %s", inArgs, outTypes);
        emitter().emit("{");
        emitter().emit("");
        emitter().increaseIndentation();

        // 4.2 Generate the body of the network excluding the return statement
        generateTopNetworkBody(srcToTgt, network.getInstances());

        // 4.3 Generate the required func.return for the func.func operand
        emitter().emit("// -- Return ");
        emitter().emit("func.return %s: %s", outOperands, outTypes);

        // 4.4 Done with the @top operation, close it.
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void generateTopNetworkBody(Map<Connection.End, List<Connection.End>> srcToTgt, List<Instance> instances) {
        // These 4 arrays contains different sections of the network body that need to be generated:
        // We fill these arrays at the start and then output them via the emitter in the correct order at the end.
        // 1. Channel instantiation: eg '%q1_in, %q1_out = dfg.channel(4) : i32'
        // 2. Connect output ports to channels: eg '%0 = dfg.pull %q5_out : i32'
        // 3. Connect input ports to channels: eg 'dfg.push(%in1) %q1_in : i32'
        // 4. Instantiate the actors/entities/node: eg 'dfg.instantiate @adder inputs(%q1_out, %q2_out) outputs
        //                                                  (%q4_in) : (i32, i32) -> i32'
        // Points 1,2 and 3 are closely related, so they are all generated within the same for-loop at the start but
        // then emitted separately at the end.
        List<String> instantiatedChannels = new ArrayList<>();
        List<String> connectChannelsToInPorts = new ArrayList<>();
        List<String> connectChannelsToOutPorts = new ArrayList<>();
        List<String> instanceInstantiation = new ArrayList<>();

        // We need 
        for (Map.Entry<Connection.End, List<Connection.End>> entry : srcToTgt.entrySet()) {
            // 1. Channel instantiation
            String channelInput = "", channelOutput = "", type = "";
            int channelSize;
            Connection.End src = entry.getKey();
            if (src.getInstance().isPresent()) {
                channelInput = src.getInstance().get() + "_";
                type = backend().typeseval().type(backend().channelsutils().sourceEndType(src)).toString();
            }
            channelInput = channelInput + src.getPort();

            for (Connection.End tgt : entry.getValue()) {
                if (tgt.getInstance().isPresent()) {
                    channelOutput = tgt.getInstance().get() + "_";

                    // Type is checked in two places because sometimes the src is missing from the channel and sometimes
                    // the target is missing but never both, if we check on both the src and tgt, we are guaranteed to
                    // get the type.
                    type = backend().typeseval().type(backend().channelsutils().targetEndType(tgt)).toString();
                }
                channelOutput = channelOutput + tgt.getPort();
                channelSize = backend().channelsutils().connectionBufferSize(new Connection(src, tgt));
                instantiatedChannels.add(("%%queue_from_" + channelInput + ", %%queue_to_" + channelOutput + " = dfg" +
                        ".channel(" + channelSize + ") : " + type));

                // 2. Connect output ports to channels
                if (!tgt.getInstance().isPresent()) {
                    connectChannelsToOutPorts.add("%%" + channelOutput + " = dfg.pull %%queue_to" + channelOutput +
                            " : " + type);
                }
            }

            // 3. Connect input ports to channels
            if (!src.getInstance().isPresent()) {
                connectChannelsToInPorts.add("dfg.push(%%" + channelInput + ") %%queue_from_" + channelInput + " : " + type);
            }
        }

        // 4. Instantiate the actors/entities/node
        for (Instance instance : instances) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);

            instanceInstantiation.add("dfg.instantiate () @" + instance.getInstanceName());
            instanceInstantiation.add("\tinput(" + "" + ")");
            instanceInstantiation.add("\toutput(" + "" + ") :");
            instanceInstantiation.add("\t(" + "" + ") ->" + "");
        }

        // 5. Now emit everything that has been generated
        emitter().emit("// -- Instantiate channels between actors");
        for (String line : instantiatedChannels) {
            emitter().emit(line);
        }
        emitter().emit("");

        emitter().emit("// -- Connect input channels to arguments");
        for (String line : connectChannelsToInPorts) {
            emitter().emit(line);
        }
        emitter().emit("");

        emitter().emit("// -- Instantiate actors (also known as nodes/instances)");
        for (String line : instanceInstantiation) {
            emitter().emit(line);
        }
        emitter().emit("");

        emitter().emit("// -- Connect output channels to return arguments");
        for (String line : connectChannelsToOutPorts) {
            emitter().emit(line);
        }
        emitter().emit("");
    }
}
