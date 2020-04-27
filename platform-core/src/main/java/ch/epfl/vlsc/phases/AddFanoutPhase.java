package ch.epfl.vlsc.phases;

import se.lth.cs.tycho.compiler.*;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.*;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.cal.*;
import se.lth.cs.tycho.ir.expr.ExprCase;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.expr.pattern.PatternBinding;
import se.lth.cs.tycho.ir.expr.pattern.PatternWildcard;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtRead;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.ElaborateNetworkPhase;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

import java.util.*;
import java.util.stream.Collectors;

public class AddFanoutPhase implements Phase {
    @Override
    public String getName() {
        return "Add fanout on Network.";
    }

    @Override
    public String getDescription() {
        return "Add a fanout on instance output ports and input ports.";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        return addFannout(task, task.getNetwork(), context);
    }

    public CompilationTask addFannout(CompilationTask task, Network network, Context context) {

        // -- Map of fannouts per input ports and by instance output port
        Map<String, List<Connection>> portInFanouts = network.getConnections().stream()
                .filter(c -> !c.getSource().getInstance().isPresent())
                .filter(c -> network.getConnections().stream().anyMatch(u -> u.getSource().getPort().equals(c.getSource().getPort()) && !u.equals(c)))
                .collect(Collectors.groupingBy(c -> c.getSource().getPort()));

        Map<String, Map<String, List<Connection>>> instancePortFanout = network.getConnections().stream()
                .filter(c -> c.getSource().getInstance().isPresent())
                .filter(c -> network.getConnections().stream().
                        filter(x -> x.getSource().getInstance().isPresent())
                        .anyMatch(u -> u.getSource().getInstance().get().equals(c.getSource().getInstance().get()) && u.getSource().getPort().equals(c.getSource().getPort()) && !u.equals(c)))
                .collect(Collectors.groupingBy(c -> c.getSource().getInstance().get()))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().stream().collect(Collectors.groupingBy(c -> c.getSource().getPort()))));

        // -- If both maps are empty return original task
        if (portInFanouts.isEmpty() && instancePortFanout.isEmpty()) {
            return task;
        }

        // --------------------------------------------------------------------
        // -- Network Immutable lists

        // -- Copy I/O Ports
        ImmutableList<PortDecl> networkInputPorts = network.getInputPorts().map(PortDecl::deepClone);
        ImmutableList<PortDecl> networkOutputPorts = network.getOutputPorts().map(PortDecl::deepClone);

        // -- Network Instances
        ImmutableList.Builder<Instance> networkInstances = ImmutableList.builder();
        for (Instance instance : network.getInstances()) {
            networkInstances.add(instance);
        }

        // -- Network connections
        ImmutableList.Builder<Connection> networkConnections = ImmutableList.builder();


        // -- Global entities
        ImmutableList.Builder<GlobalEntityDecl> fanoutEntities = ImmutableList.builder();


        // --------------------------------------------------------------------
        // -- Fanout Actor for input ports
        List<Connection> oldConnections = new ArrayList<>();
        for (Connection c : network.getConnections()) {
            oldConnections.add(c);
        }

        // -- Create fanout actor and entities
        for (String portName : portInFanouts.keySet()) {
            // -- Name
            String name = "fanout_port_" + portName;

            // -- Get Input Port Declaration
            PortDecl port = networkInputPorts.stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);

            // -- Create actor
            CalActor fanout = getFanoutActor(port, portInFanouts.get(portName).size());

            // -- Names
            String fanoutInstanceName = uniqueInstanceName(task.getNetwork(), name);
            String originalEntityName = name;

            // -- Create global entity
            GlobalEntityDecl fanoutEntity = GlobalEntityDecl.global(Availability.PUBLIC, originalEntityName, fanout, false);

            // -- Create instance
            Instance instance = new Instance(fanoutInstanceName, QID.of(originalEntityName), ImmutableList.empty(), ImmutableList.empty());
            networkInstances.add(instance);

            // -- Create Connection from Port to Fanout
            {
                Connection.End source = new Connection.End(Optional.empty(), portName);
                Connection.End target = new Connection.End(Optional.of(name), "F_IN");
                Connection connection = new Connection(source, target);
                networkConnections.add(connection);
            }

            // -- Create Connections from fanout to instance/ports
            int i = 0;
            for (Connection fanoutConnection : portInFanouts.get(portName)) {
                Connection.End source = new Connection.End(Optional.of(name), "F_OUT_" + i);
                Connection.End target = new Connection.End(Optional.of(fanoutConnection.getTarget().getInstance().get()), fanoutConnection.getTarget().getPort());
                Connection connection = new Connection(source, target);

                // -- Remove old connection
                Connection oldConnection = oldConnections.stream().filter(c -> c.equals(fanoutConnection)).findAny().orElse(null);
                oldConnections.remove(oldConnection);

                // -- Add new Connection to network Connections
                networkConnections.add(connection);
                i++;
            }

            fanoutEntities.add(fanoutEntity);
        }

        // --------------------------------------------------------------------
        // -- Fanout Actor for instance outputs
        for (String instanceName : instancePortFanout.keySet()) {
            for (String portName : instancePortFanout.get(instanceName).keySet()) {
                // -- String name
                String name = "fanout_" + instanceName + "_port_" + portName;

                // -- Get instance and global entity declaration
                Instance instance = network.getInstances().stream().filter(i -> i.getInstanceName().equals(instanceName)).findAny().orElse(null);
                GlobalEntityDecl entity = GlobalDeclarations.getEntity(task, instance.getEntityName());

                // -- Get output port
                PortDecl port = entity.getEntity().getOutputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);

                // -- Create actor
                CalActor fanout = getFanoutActor(port, instancePortFanout.get(instanceName).get(portName).size());

                // -- Names
                String fanoutInstanceName = uniqueInstanceName(task.getNetwork(), name);
                String originalEntityName = name;

                // -- Create global entity
                GlobalEntityDecl fanoutEntity = GlobalEntityDecl.global(Availability.PUBLIC, originalEntityName, fanout, false);
                fanoutEntities.add(fanoutEntity);

                // -- Create instance
                Instance fanoutInstance = new Instance(fanoutInstanceName, QID.of(originalEntityName), ImmutableList.empty(), ImmutableList.empty());
                networkInstances.add(fanoutInstance);

                // -- Create Connection from Port to Fanout
                {
                    Connection.End source = new Connection.End(Optional.of(instanceName), portName);
                    Connection.End target = new Connection.End(Optional.of(name), "F_IN");
                    Connection connection = new Connection(source, target);
                    networkConnections.add(connection);
                }

                // -- Create Connections from fanout to instance/ports
                int i = 0;
                for (Connection fanoutConnection : instancePortFanout.get(instanceName).get(portName)) {
                    Connection.End source = new Connection.End(Optional.of(name), "F_OUT_" + i);

                    Optional<String> targetInstance;
                    if (fanoutConnection.getTarget().getInstance().isPresent()) {
                        targetInstance = Optional.of(fanoutConnection.getTarget().getInstance().get());
                    } else {
                        targetInstance = Optional.empty();
                    }

                    Connection.End target = new Connection.End(targetInstance, fanoutConnection.getTarget().getPort());
                    Connection connection = new Connection(source, target);

                    // -- Remove old connection
                    Connection oldConnection = oldConnections.stream().filter(c -> c.equals(fanoutConnection)).findAny().orElse(null);
                    oldConnections.remove(oldConnection);

                    // -- Add new Connection to network Connections
                    networkConnections.add(connection);
                    i++;
                }

            }
        }

        // -- Copy remaining old connection to the new network connections
        for (Connection c : oldConnections) {
            Optional<String> sourceInstance;
            Optional<String> targetInstance;

            if (c.getSource().getInstance().isPresent()) {
                sourceInstance = Optional.of(c.getSource().getInstance().get());
            } else {
                sourceInstance = Optional.empty();
            }

            if (c.getTarget().getInstance().isPresent()) {
                targetInstance = Optional.of(c.getTarget().getInstance().get());
            } else {
                targetInstance = Optional.empty();
            }

            Connection.End source = new Connection.End(sourceInstance, c.getSource().getPort());
            Connection.End target = new Connection.End(targetInstance, c.getTarget().getPort());
            Connection connection = new Connection(source, target);
            networkConnections.add(connection);
        }


        // --------------------------------------------------------------------
        // -- Create new Network
        Network result = new Network(networkInputPorts, networkOutputPorts, networkInstances.build(), networkConnections.build());

        task = task.withNetwork(result);

        SourceUnit fanoutUnit = new SyntheticSourceUnit(new NamespaceDecl(QID.empty(), null, null, fanoutEntities.build(), null));

        return task.withSourceUnits(ImmutableList.<SourceUnit>builder().addAll(task.getSourceUnits()).add(fanoutUnit).build());
    }

    private CalActor getFanoutActorProcess(PortDecl port, int fanoutSize) {
        // -- Fanout Actor Ports
        PortDecl in = new PortDecl("F_IN", (TypeExpr) port.getType().deepClone());
        ImmutableList.Builder<PortDecl> fanoutInputPorts = ImmutableList.builder();
        fanoutInputPorts.add(in);

        ImmutableList.Builder<PortDecl> fanoutOutputPorts = ImmutableList.builder();
        for (int i = 0; i < fanoutSize; i++) {
            PortDecl out = new PortDecl("F_OUT_" + i, (TypeExpr) port.getType().deepClone());
            fanoutOutputPorts.add(out);
        }


        // -- Local variables
        ImmutableList.Builder<LocalVarDecl> varDecls = ImmutableList.builder();
        LocalVarDecl token = new LocalVarDecl((TypeExpr) port.getType().deepClone(), "token", null, false);
        varDecls.add(token);


        // -- Statements
        ImmutableList.Builder<Statement> statements = ImmutableList.builder();

        // -- Read Statement
        Port inPort = new Port("F_IN");

        LValueVariable lvalue = new LValueVariable(Variable.variable("token"));
        ImmutableList.Builder<LValue> lvalues = ImmutableList.builder();
        lvalues.add(lvalue);

        StmtRead read = new StmtRead(inPort, lvalues.build(), null);
        statements.add(read);

        // -- Write Statements
        for (int i = 0; i < fanoutSize; i++) {
            Port outPort = new Port("F_OUT_" + i);
            ExprVariable exprVar = new ExprVariable(Variable.variable("token"));
            ImmutableList.Builder<Expression> expressions = ImmutableList.builder();
            expressions.add(exprVar);
            StmtWrite write = new StmtWrite(outPort, expressions.build(), null);
            statements.add(write);
        }


        // -- Process
        ProcessDescription process = new ProcessDescription(statements.build(), true);

        CalActor fanout = new CalActor(ImmutableList.empty(),
                ImmutableList.empty(),
                ImmutableList.empty(),
                varDecls.build(),
                fanoutInputPorts.build(),
                fanoutOutputPorts.build(),
                ImmutableList.empty(),
                ImmutableList.empty(),
                null,
                process,
                ImmutableList.empty(),
                ImmutableList.empty());
        return fanout;
    }


    private CalActor getFanoutActor(PortDecl port, int fanoutSize) {
        // -- Fanout Actor Ports
        PortDecl in = new PortDecl("F_IN", (TypeExpr) port.getType().deepClone());
        ImmutableList.Builder<PortDecl> fanoutInputPorts = ImmutableList.builder();
        fanoutInputPorts.add(in);

        ImmutableList.Builder<PortDecl> fanoutOutputPorts = ImmutableList.builder();
        for (int i = 0; i < fanoutSize; i++) {
            PortDecl out = new PortDecl("F_OUT_" + i, (TypeExpr) port.getType().deepClone());
            fanoutOutputPorts.add(out);
        }

        // -- Input Port
        Port inPort = new Port("F_IN");

        // -- Matches
        ImmutableList.Builder<Match> matches = ImmutableList.builder();

        ImmutableList.Builder<ExprCase.Alternative> alternativeBuilder = ImmutableList.builder();
        ExprCase.Alternative aTrue = new ExprCase.Alternative(
                new PatternBinding(new PatternVarDecl("token")), ImmutableList.empty(), new ExprLiteral(ExprLiteral.Kind.True)
        );
        ExprCase.Alternative aFalse = new ExprCase.Alternative(
                new PatternWildcard(), ImmutableList.empty(), new ExprLiteral(ExprLiteral.Kind.False)
        );

        alternativeBuilder.add(aTrue);
        alternativeBuilder.add(aFalse);
        ExprCase exprCase = new ExprCase(new ExprVariable(Variable.variable("$token")), alternativeBuilder.build());

        Match match = new Match(VarDecl.input("$token"), exprCase);
        matches.add(match);

        // -- Input pattern
        ImmutableList.Builder<InputPattern> inputPatters = ImmutableList.builder();
        InputPattern inputPattern = new InputPattern(inPort, matches.build(), null);
        inputPatters.add(inputPattern);

        // -- Output Expression
        ImmutableList.Builder<OutputExpression> outputExpressions = ImmutableList.builder();

        for (int i = 0; i < fanoutSize; i++) {
            Port outPort = new Port("F_OUT_" + i);
            ExprVariable exprVar = new ExprVariable(Variable.variable("token"));
            ImmutableList.Builder<Expression> expressions = ImmutableList.builder();
            expressions.add(exprVar);
            OutputExpression outputExpression = new OutputExpression(outPort, expressions.build(), null);
            outputExpressions.add(outputExpression);
        }

        // -- Action
        ImmutableList.Builder<Action> actions = ImmutableList.builder();
        Action action = new Action(QID.of("untagged"),
                inputPatters.build(),
                outputExpressions.build(),
                ImmutableList.empty(),
                ImmutableList.empty(),
                ImmutableList.empty(),
                ImmutableList.empty(),
                null,
                ImmutableList.empty(),
                ImmutableList.empty()
        );
        actions.add(action);

        // -- Cal Actor
        CalActor fanout = new CalActor(
                ImmutableList.empty(),
                ImmutableList.empty(),
                ImmutableList.empty(),
                ImmutableList.empty(),
                fanoutInputPorts.build(),
                fanoutOutputPorts.build(),
                ImmutableList.empty(),
                actions.build(),
                null,
                null,
                ImmutableList.empty(),
                ImmutableList.empty());
        return fanout;
    }


    private String uniqueInstanceName(Network network, String base) {
        Set<String> names = network.getInstances().stream()
                .map(Instance::getInstanceName)
                .collect(Collectors.toSet());
        String result = base;
        int i = 1;
        while (names.contains(result)) {
            result = String.format("%s_%d", base, i);
            i = i + 1;
        }
        return result;
    }

    @Override
    public Set<Class<? extends Phase>> dependencies() {
        return Collections.singleton(ElaborateNetworkPhase.class);
    }
}
