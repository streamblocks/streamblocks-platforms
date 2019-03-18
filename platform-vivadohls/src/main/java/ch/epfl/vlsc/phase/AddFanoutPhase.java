package ch.epfl.vlsc.phase;

import ch.epfl.vlsc.backend.Variables;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.Availability;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.entity.cal.ProcessDescription;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtRead;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.ElaborateNetworkPhase;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return task.withNetwork(addFannout(task, task.getNetwork()));
    }

    public Network addFannout(CompilationTask task, Network network) {

        // -- Global entities
        ImmutableList.Builder<GlobalEntityDecl> fanoutEntities = ImmutableList.builder();

        // -- Copy I/O Ports
        ImmutableList<PortDecl> inputPorts = network.getInputPorts().map(PortDecl::deepClone);
        ImmutableList<PortDecl> outputPorts = network.getOutputPorts().map(PortDecl::deepClone);

        // -- Copy Instances
        ImmutableList.Builder<Instance> instances = ImmutableList.builder();
        for (Instance instance : network.getInstances()) {
            instances.add(instance);
        }
        // --------------------------------------------------------------------
        // -- Fanout Actor for input ports
        Map<String, List<Connection>> portInFanouts = network.getConnections().stream()
                .filter(c -> !c.getSource().getInstance().isPresent())
                .collect(Collectors.groupingBy(c -> c.getSource().getPort()));


        // -- Create fanout actor and entities
        for (String name : portInFanouts.keySet()) {
            // -- Get Input Port Declaration
            PortDecl port = inputPorts.stream().filter(p -> p.getName().equals(name)).findAny().orElse(null);

            // -- Create actor
            CalActor fanout = getFanoutActor(port, portInFanouts.get(name).size());

            // -- Create global entity
            GlobalEntityDecl fanoutEntity = GlobalEntityDecl.global(Availability.PUBLIC, "fanout_port_" + name, fanout, true);
            fanoutEntities.add(fanoutEntity);
        }

        // --------------------------------------------------------------------
        // -- Fanout Actor for instance outputs
        Map<String, Map<String, List<Connection>>> instancePortFanout = network.getConnections().stream()
                .filter(c-> c.getSource().getInstance().isPresent())
                .collect(Collectors.groupingBy(c -> c.getSource().getInstance().get()))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e-> e.getKey(), e -> e.getValue().stream().collect(Collectors.groupingBy(c -> c.getSource().getPort()))));

        // -- Fixme: get isntance port form entity
        /*
        for(String instanceName : instancePortFanout.keySet()){
            for(String portName : instancePortFanout.get(instanceName).keySet()){
                // -- Get Input Port Declaration

                PortDecl port = inputPorts.stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);

                // -- Create actor
                CalActor fanout = getFanoutActor(port, portInFanouts.get(portName).size());

                // -- Create global entity
                GlobalEntityDecl fanoutEntity = GlobalEntityDecl.global(Availability.PUBLIC, "fanout_" + instanceName + "_port_" + portName, fanout, true);
                fanoutEntities.add(fanoutEntity);
            }
        }
        */


        return network;
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


    @Override
    public Set<Class<? extends Phase>> dependencies() {
        return Collections.singleton(ElaborateNetworkPhase.class);
    }
}
