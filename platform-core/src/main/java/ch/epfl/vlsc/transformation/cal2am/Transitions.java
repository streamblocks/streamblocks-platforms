package ch.epfl.vlsc.transformation.cal2am;

import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.InputVarDecl;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.entity.cal.Action;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.entity.cal.InputPattern;
import se.lth.cs.tycho.ir.entity.cal.OutputExpression;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtRead;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.transformation.cal2am.Conditions;
import se.lth.cs.tycho.transformation.cal2am.Scopes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Transitions {
    private final CalActor actor;
    private final Conditions conditions;
    private final ImmutableList<Integer> transientScopes;
    private boolean initialized;
    private ImmutableList<Transition> transitions;
    private Map<Action, Integer> indexMap;

    public Transitions(CalActor actor, Scopes scopes, Conditions conditions) {
        this.actor = actor;
        this.conditions = conditions;
        this.transientScopes = scopes.getTransientScopes().stream().boxed().collect(ImmutableList.collector());
        this.initialized = false;
    }

    private void init() {
        if (!initialized) {
            this.transitions = actor.getActions().stream()
                    .map(this::actionToTransition)
                    .collect(ImmutableList.collector());
            this.indexMap = new HashMap<>();
            int i = 0;
            for (Action action : actor.getActions()) {
                indexMap.put(action, i++);
            }
            initialized = true;
        }
    }

    private Transition actionToTransition(Action action) {
        ImmutableList.Builder<Statement> builder = ImmutableList.builder();
        addInputStmts(action.getInputPatterns(), builder);
        builder.addAll(action.getBody());
        addOutputStmts(action.getOutputExpressions(), builder);
        return new Transition(getInputRates(action.getInputPatterns()), getOutputRates(action.getOutputExpressions()), transientScopes, builder.build());
    }

    private Map<Port, Integer> getOutputRates(ImmutableList<OutputExpression> outputExpressions) {
        return outputExpressions.stream()
                .map(conditions::getCondition)
                .map(PortCondition::deepClone)
                .collect(Collectors.toMap(PortCondition::getPortName, PortCondition::N));
    }

    private Map<Port, Integer> getInputRates(ImmutableList<InputPattern> inputPatterns) {
        return inputPatterns.stream()
                .map(conditions::getCondition)
                .map(PortCondition::deepClone)
                .collect(Collectors.toMap(PortCondition::getPortName, PortCondition::N));
    }

    private void addInputStmts(ImmutableList<InputPattern> inputPatterns, Consumer<Statement> builder) {
        for(InputPattern inputPattern : inputPatterns){
            ImmutableList.Builder<LValue> lvalues = ImmutableList.builder();
            for(InputVarDecl var : inputPattern.getVariables()){
                LValueVariable lvalue = new LValueVariable(Variable.variable(var.getName()));
                lvalues.add(lvalue);
            }
            Statement read = new StmtRead((Port) inputPattern.getPort().deepClone(), lvalues.build(), inputPattern.getRepeatExpr());
            builder.accept(read);
        }

    }

    private void addOutputStmts(ImmutableList<OutputExpression> outputExpressions, Consumer<Statement> builder) {
        outputExpressions.stream()
                .map(output -> new StmtWrite((Port) output.getPort().deepClone(), output.getExpressions(), output.getRepeatExpr()))
                .forEach(builder);
    }

    public List<Transition> getAllTransitions() {
        init();
        return transitions;
    }

    public Transition getTransition(Action action) {
        init();
        return transitions.get(getTransitionIndex(action));
    }

    public int getTransitionIndex(Action action) {
        init();
        if (indexMap.containsKey(action)) {
            return indexMap.get(action);
        } else {
            throw new IllegalArgumentException();
        }
    }
}
