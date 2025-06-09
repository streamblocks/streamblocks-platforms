package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.ir.PartitionHandle.Pair;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.util.*;

/**
 * TODO: Comment here
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
        defineEntities();
        initNetwork();
    }

    default void defineEntities() {

        Set<String> definedInstancesClasses = new HashSet<>(backend().task().getNetwork().getInstances().size());

        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String entityClass = entityDecl.getOriginalName();
            // Check if this specific instance class has been defined, if not, we define it or else we skip this

            if(backend().context().getConfiguration().get(PlatformSettings.generateSingleDeclarationPerActor)) {
                if (definedInstancesClasses.add(entityClass)) {
                    backend().instance().generateInstance(instance);
                    emitter().emitNewLine();
                }
            }else{
                backend().instance().generateInstance(instance);
                emitter().emitNewLine();
            }
        }

    }

    /**
     * Generate a top module for the CAL MLIR dialect. This is very, very rough right now. That's why so much is
     * commented out
     * <p>
     * Here is an example of what we need to generate:
     * <p>
     * // -- Top Network: Defines structure of actor application
     * cal.network
     * {
     * ....// -- Instantiate channels between actors
     * ....%queue_from_pass_Out, %queue_to_sink_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo.output_port<i32>
     * ....%queue_from_source_Out, %queue_to_pass_In = fifo.create<i32>(1) : !fifo.input_port<i32>, !fifo
     * .output_port<i32>
     * ....// -- Instantiate actors (also known as nodes/instances)
     * ....cal.create_instance @Source "source" ()
     * ....ports_out(%queue_from_source_Out: !fifo.input_port<i32>)
     * ....cal.create_instance @Pass "pass" ()
     * ....ports_in(%queue_to_pass_In: !fifo.output_port<i32>)
     * ....ports_out(%queue_from_pass_Out: !fifo.input_port<i32>)
     * ....cal.create_instance @Sink "sink" ()
     * ....ports_in(%queue_to_sink_In: !fifo.output_port<i32>)
     * }
     */
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

        // THIS COMMENTED OUT BLOCK OF CODE DOES NOT WORK - It is a relic from when I tried convert CAL to the DFG
        // dialect, it could be useful as I update the cal dialect. So I have left it in but it means nothing
        // 2. Generate the list of input operands for the %top operation:
        //  In the example: "func.func @top(%in1: i32, %in2: i32, %in3: i32)", %in3: i32, is one input operand
        //  with both the name and return type specified. We generate the list op operands in this format
//        String inArgs = "";
//        if (!network.getInputPorts().isEmpty()) {
//            for (PortDecl port : network.getInputPorts()) {
//                // We need the name of the port as well as the token type. We can get the token type from
//                // channelUtils, but we need a Connection.End object.
//                // This is present in srctoTgt map, but to search this is unecessary if we can just regenerate the
//                // object.
//                Connection.End end = new Connection.End(Optional.empty(), port.getName());
//                String tokenType = backend().typeseval().type(backend().channelsutils().sourceEndType(end))
//                .toString();
//                inArgs = inArgs + "%" + port.getName() + ": " + tokenType + ", ";
//            }
//            inArgs = inArgs.substring(0, inArgs.length() - 2);
//        }

        // 3. The return  from the top needs to be specified in two places:
        //      1. The list of return types in the @top function: @top(...) -> i32, i32  //(i32, i32 are the return
        //      types)
        //      2. In the func.return operation as "func.return %0, %1 : i32, i32" // %0 %1 are the operands to return
        //      with i32, i32 being their type.
        // We generate the lists of types and operands separately, to be combined later as needed.
//        String outOperands = "";
//        String outTypes = "";
//        if (!network.getOutputPorts().isEmpty()) {
//            for (PortDecl port : network.getOutputPorts()) {
//                Connection.End end = new Connection.End(Optional.empty(), port.getName());
//                String tokenType = backend().typeseval().type(backend().channelsutils().targetEndType(end))
//                .toString();
//                outTypes = outTypes + tokenType + ", ";
//                outOperands = outOperands + "%" + port.getName() + ", ";
//            }
//            outOperands = outOperands.substring(0, outOperands.length() - 2);
//            outTypes = outTypes.substring(0, outTypes.length() - 2);
//        }

        // 4. We now finally have everything we need for the func operation, so lets generate it
        // 4.1 Generate the first line of the cal.network operation
        emitter().emit("// -- Top Network: Defines structure of actor application");
        emitter().emit("cal.network");
        emitter().emit("{");
        emitter().emit("");
        emitter().increaseIndentation();

        // 4.2 Generate the body of the network
        generateTopNetworkBody(srcToTgt, network.getInstances());


        // 4.4 Done with the main network operation, close it.
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
    }

    /**
     * Generates the MLIR body of the `cal.network` operation, which defines the structure of the dataflow
     * network by connecting instances (actors) via FIFO channels.
     * <p>
     * This method builds the top-level interconnect between actor instances by:
     * <ol>
     *     <li>Instantiating FIFO channels between source and target ports.</li>
     *     <li>(Not yet supported) Connecting top-level network input ports to FIFO channels.</li>
     *     <li>(Not yet supported) Connecting FIFO channels to top-level network output ports.</li>
     *     <li>Instantiating actors and wiring their input and output ports to the appropriate channels.</li>
     * </ol>
     * The method first constructs textual representations of each network component in separate buffers,
     * which are then emitted in order to preserve the structure and readability of the generated MLIR code.
     * <p>
     * <strong>Limitations:</strong> Due to current CAL-to-MLIR backend constraints, support for network-level
     * input and output ports is not implemented. As a result, the logic related to these ports is commented out.
     *
     * @param srcToTgt  A mapping from each source port (as {@link Connection.End}) to its corresponding list
     *                  of target ports. This determines the FIFO connections to generate.
     * @param instances The list of actor instances to instantiate within the network.
     */
    default void generateTopNetworkBody(Map<Connection.End, List<Connection.End>> srcToTgt, List<Instance> instances) {
        // These 4 arrays contains different sections of the network body that need to be generated:
        // We fill these arrays at the start and then output them via the emitter in the correct order at the end.
        // 1. Channel instantiation: eg '%q1_in, %q1_out = dfg.channel(4) : i32'
        // 2. Connect output ports to channels - not supported in cal.network operation in MLIR yet
        // 3. Connect input ports to channels - not supported in cal.network operation in MLIR yet
        // 4. Instantiate the actors/entities/node: eg 'dfg.instantiate @adder inputs(%q1_out, %q2_out) outputs
        //                                                  (%q4_in) : (i32, i32) -> i32'
        // Points 1,2 and 3 are closely related, so they are all generated within the same for-loop at the start but
        // then emitted separately at the end.
        List<String> instantiatedChannels = new ArrayList<>();
        List<String> connectChannelsToInPorts = new ArrayList<>();
        List<String> connectChannelsToOutPorts = new ArrayList<>();
        List<String> instanceInstantiation = new ArrayList<>();

        for (Map.Entry<Connection.End, List<Connection.End>> entry : srcToTgt.entrySet()) {
            // 1. Channel instantiation
            String channelInput = "", channelOutput = "", type = "";
            int channelSize = -1;
            Connection.End src = entry.getKey();
            if (src.getInstance().isPresent()) {
                channelInput = src.getInstance().get() + "_";
                type = backend().typeseval().type(backend().channelsutils().sourceEndType(src)).toString();
                channelSize = backend().channelsutils().sourceEndSize(src);
            }
            channelInput = channelInput + src.getPort();

            for (Connection.End tgt : entry.getValue()) {
                if (tgt.getInstance().isPresent()) {
                    channelOutput = tgt.getInstance().get() + "_";

                    // Type and channelSize is checked in two places because sometimes the src is missing from the
                    // channel and sometimes he target is missing but never both, if we check on both the src and
                    // tgt, we are guaranteed to get the type and size.
                    type = backend().typeseval().type(backend().channelsutils().targetEndType(tgt)).toString();
                    channelSize = backend().channelsutils().targetEndSize(tgt);
                }
                channelOutput = channelOutput + tgt.getPort();
                instantiatedChannels.add(("%%queue_from_" + channelInput + ", %%queue_to_" + channelOutput + " = fifo" +
                        ".create<" + type + ">(" + channelSize + ") : !fifo.input_port<" + type + ">, !fifo" +
                        ".output_port<" + type + ">"));

                // 2. Connect output ports to channels
                //if (!tgt.getInstance().isPresent()) {
                //    connectChannelsToOutPorts.add("%%" + channelOutput + " = dfg.pull %%queue_to_" + channelOutput +
                //            " : " + type);
                //}
            }

            // 3. Connect input ports to channels
            //if (!src.getInstance().isPresent()) {
            //    connectChannelsToInPorts.add("dfg.push(%%" + channelInput + ") %%queue_from_" + channelInput + " :
            //    " + type);
            //}
        }

        // 4. Instantiate the actors/entities/node
        for (Instance instance : instances) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String entityName = instance.getInstanceName(); // Don't need this in DFG
            String entityClass = entityDecl.getOriginalName();

            // 4.1 Generate types and names of the input ports for the entity
            String inputPortNames = "";
            String inputPortTypes = "";
            if (!entityDecl.getEntity().getInputPorts().isEmpty()) {
                for (Pair<PortDecl, String> pair : backend().channelsutils().getInputPortNamesAndTypes(entityName,
                        entityDecl)) {
                    inputPortTypes = inputPortTypes + "!fifo.output_port<" + pair._2 + ">, ";
                    inputPortNames = inputPortNames + "%%queue_to_" + entityName + "_" + pair._1 + ", ";
                }
                inputPortNames = inputPortNames.substring(0, inputPortNames.length() - 2);
                inputPortTypes = inputPortTypes.substring(0, inputPortTypes.length() - 2);
            }

            // 4.2 Generate types and names of the output ports of the entity
            String outputPortNames = "";
            String outputPortTypes = "";
            if (!entityDecl.getEntity().getOutputPorts().isEmpty()) {
                for (Pair<PortDecl, String> pair : backend().channelsutils().getOutputPortNamesAndTypes(entityName,
                        entityDecl)) {
                    outputPortTypes = outputPortTypes + "!fifo.input_port<" + pair._2 + ">, ";
                    outputPortNames = outputPortNames + "%%queue_from_" + entityName + "_" + pair._1 + ", ";
                }
                outputPortNames = outputPortNames.substring(0, outputPortNames.length() - 2);
                outputPortTypes = outputPortTypes.substring(0, outputPortTypes.length() - 2);
            }

            // 4.3 Generate the MLIR for the actor using everything we have generated
            if(backend().context().getConfiguration().get(PlatformSettings.generateSingleDeclarationPerActor)) {
                instanceInstantiation.add("cal.create_instance @" + entityClass + " \"" + entityName + "\" ()");
            }else{
                instanceInstantiation.add("cal.create_instance @" + entityName + " \"" + entityName + "\" ()");
            }
            if (!inputPortNames.isEmpty()) {
                instanceInstantiation.add("\tports_in(" + inputPortNames + ": " + inputPortTypes + ")");
            }
            if (!outputPortNames.isEmpty()) {
                instanceInstantiation.add("\tports_out(" + outputPortNames + ": " + outputPortTypes + ")");
            }
        }

        // 5. Now emit everything that has been generated
        emitter().emit("// -- Instantiate channels between actors");
        for (String line : instantiatedChannels) {
            emitter().emit(line);
        }
        emitter().emit("");

        /*emitter().emit("// -- Connect input channels to arguments");
        for (String line : connectChannelsToInPorts) {
            emitter().emit(line);
        }
        emitter().emit("");*/

        emitter().emit("// -- Instantiate actors (also known as nodes/instances)");
        for (String line : instanceInstantiation) {
            emitter().emit(line);
        }
        emitter().emit("");

        /*emitter().emit("// -- Connect output channels to return arguments");
        for (String line : connectChannelsToOutPorts) {
            emitter().emit(line);
        }
        emitter().emit("");*/
    }
}
