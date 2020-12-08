package ch.epfl.vlsc.hls.phase;

import ch.epfl.vlsc.compiler.ir.BankedPortDecl;
import ch.epfl.vlsc.settings.PlatformSettings;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.settings.Setting;

import javax.tools.JavaCompiler;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BankedNetworkPortsPhase implements Phase {


    private int getNumberOfBanks() {
        return 4;
    }
    @Override
    public String getDescription() {
        return "Transforms all PortDecl nodes in the network i/o to BankedPortDecl";
    }



    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        if (context.getConfiguration().get(PlatformSettings.enableSystemC)) {
            return task;
        }
        Network network = task.getNetwork();

        ImmutableList.Builder<PortDecl> inputPortsBuilder = ImmutableList.builder();

        int numBanks = getNumberOfBanks();
        for (PortDecl inputPort : network.getInputPorts()) {
            int portId = network.getInputPorts().indexOf(inputPort);
            inputPortsBuilder.add(makeBankedPort(inputPort, portId));
        }

        ImmutableList.Builder<PortDecl> outputPortsBuilder = ImmutableList.builder();

        for (PortDecl outputPort : network.getOutputPorts()) {
            int portId = network.getOutputPorts().indexOf(outputPort);
            outputPortsBuilder.add(makeBankedPort(outputPort, portId));
        }

        ImmutableList<PortDecl> inputPorts = inputPortsBuilder.build();
        ImmutableList<PortDecl> outputPorts = outputPortsBuilder.build();

        ImmutableList<Connection> connections = network.getConnections().map(connection -> {
            if (isInputConnection(network, connection)) {
                PortDecl inputPort = network.getInputPorts().stream().filter(p ->
                        p.getName().equals(connection.getSource().getPort())).findFirst().orElseThrow(
                        () -> new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,
                                "Could not find the input port " + connection.getSource().getPort()))
                );

                int portIndex = network.getInputPorts().indexOf(inputPort);
                PortDecl bankedPort = inputPorts.get(portIndex);
                Connection.End source = new Connection.End(Optional.empty(), bankedPort.getName());
                return connection.withSource(source);
            } else if (isOutputConnection(network, connection)) {
                PortDecl outputPort = network.getOutputPorts().stream().filter(p ->
                        p.getName().equals(connection.getTarget().getPort())).findFirst().orElseThrow(
                        () -> new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,
                                "Could not find the input port " + connection.getTarget().getPort()))
                );
                int portIndex = network.getOutputPorts().indexOf(outputPort);
                PortDecl bankedPort = outputPorts.get(portIndex);
                Connection.End target = new Connection.End(Optional.empty(), bankedPort.getName());
                return connection.withTarget(target);
            } else {
                return connection.deepClone();
            }
        });


        Network transformedNetwork = network.withConnections(connections)
                .withInputPorts(inputPorts).withOutputPorts(outputPorts);

        CompilationTask transformedTask = task.withNetwork(transformedNetwork);

        return transformedTask;
    }

    private BankedPortDecl makeBankedPort(PortDecl port, int portId) {
        int bankId = portId % getNumberOfBanks();
        String bankedName = port.getName().toLowerCase() + "_banked_" + portId;
        return new BankedPortDecl(bankedName, bankId, port.getType());
    }

    private boolean isInputConnection(Network network, Connection connection) {

        return network.getInputPorts().stream().anyMatch(p ->
                p.getName().equals(connection.getSource().getPort()) &&
                        !connection.getSource().getInstance().isPresent());
    }
    private boolean isOutputConnection(Network network, Connection connection) {
        return network.getOutputPorts().stream().anyMatch(p ->
                p.getName().equals(connection.getTarget().getPort()) &&
                !connection.getTarget().getInstance().isPresent());
    }

}
