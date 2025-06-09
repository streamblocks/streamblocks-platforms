package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.StackSSA;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.ir.PartitionHandle;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.InputVarDecl;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.entity.cal.*;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.transformation.cal2am.Priorities;
import se.lth.cs.tycho.type.Type;

import java.util.*;
import java.util.stream.Collectors;

@Module
public interface Instances {
    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default StackSSA ssaValueNumberingStack() {
        return backend().ssaValueNumberingStack();
    }

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Declarations declarations() {
        return backend().declarations();
    }

    default Types types() {
        return backend().types();
    }

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default Statements statements() {
        return backend().statements();
    }

    default ChannelsUtils channelutils() {
        return backend().channelsutils();
    }

    default ExpressionEvaluator expressioneval() {
        return backend().expressionEval();
    }

    default CallablesInActors callablesInActors() {
        return backend().callablesInActor();
    }

    /**
     * Generates the MLIR definition for a single CAL actor instance in the `cal.actor` dialect.
     * <p>
     * This method is responsible for producing the full textual representation of a CAL actor class,
     * including its input/output ports and internal behavior (actions). It uses the metadata
     * available in the `Instance` object to determine the actor’s name, port structure, and entity
     * definition.
     * <p>
     * Key steps performed by this method:
     * <ul>
     *   <li>Generates MLIR port signatures using the FIFO type system for input and output ports.</li>
     *   <li>Emits the `cal.actor` declaration header, including ports if applicable.</li>
     *   <li>Invokes `genActions` to emit each of the actor’s CAL actions.</li>
     * </ul>
     * <p>
     * Assumptions & Dependencies:
     * <ul>
     *   <li>Relies on `channelutils()` to determine FIFO port types.</li>
     *   <li>Assumes actor ports are defined and well-typed in the global entity declaration.</li>
     *   <li>Requires the backend infrastructure (emitter, SSA stack, type system, etc.) to be functional and
     *   correctly injected.</li>
     * </ul>
     * <p>
     * Example output (simplified):
     * <pre>
     * cal.actor @MyAdder ()
     *     ports_in(%a: !fifo.output_port<i32>, %b: !fifo.output_port<i32>)
     *     ports_out(%sum: !fifo.input_port<i32>)
     * {
     *     // -- Actor body
     *     cal.action "add" priority=0 {
     *         ...
     *     }
     * }
     * </pre>
     *
     * @param instance The CAL actor instance to be converted into MLIR actor definition.
     */
    default void generateInstance(Instance instance) {


        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        String entityClass = entityDecl.getOriginalName();
        String entityName = instance.getInstanceName();

        // These boxes store the entity and instance so that they can be accessed where required by functions deeper
        // in the entity and instance AST.
        backend().instancebox().set(instance);
        backend().entitybox().set(entityDecl.getEntity());

        List<PartitionHandle.Pair<PortDecl, String>> inputPortNamesTypes =
                channelutils().getInputPortNamesAndTypes(entityName, entityDecl);
        List<PartitionHandle.Pair<PortDecl, String>> outputPortNamesTypes =
                channelutils().getOutputPortNamesAndTypes(entityName, entityDecl);

        String inputPortString = inputPortNamesTypes.stream().map(x -> "%" + x._1 + ": !fifo.output_port<" + x._2 +
                ">").collect(Collectors.joining(","));
        String outputPortString = outputPortNamesTypes.stream().map(x -> "%" + x._1 + ": !fifo.input_port<" + x._2 +
                ">").collect(Collectors.joining(","));

        // 1. Declare the actor
        emitter().emit("//-- Definition of actor class: %s", entityClass);
        if (backend().context().getConfiguration().get(PlatformSettings.generateSingleDeclarationPerActor)) {
            emitter().emit("cal.actor @" + entityClass + " ()");
        } else {
            emitter().emit("cal.actor @" + entityName + " ()");
        }
        if (!inputPortString.isEmpty()) {
            emitter().emit("\tports_in(%s)", inputPortString);
        }
        if (!outputPortString.isEmpty()) {
            emitter().emit("\tports_out(%s)", outputPortString);
        }
        emitter().emit("{");
        emitter().increaseIndentation();

        // 2. Generate the actions of the actor
        genActions(entityDecl.getEntity());

        // 3. Generate the callables such as functions and procedures
        // genCallables(entityName, entityDecl.getEntity());

        // 4. Close the actor
        emitter().decreaseIndentation();
        emitter().emit("}");

        backend().instancebox().clear();
        backend().entitybox().clear();
    }

    void genActions(Entity entity);

    default void genActions(CalActor actor) {
        emitter().emit("// -- Actor body");

        ssaValueNumberingStack().newActorContext();
        ssaValueNumberingStack().newBlock();

        // Loop through remaining tags for each action, repeatedly selecting prioritized ones
        // and assigning them decreasing priority values (starting from highest).
        // This ensures the most important tags (as prioritized by the actor) get the highest priority.
        // Additionally designed so the lowest priority actions are always assigned a priority of 0.
        // We use logic present in the ActorMachine conversion pass (Priorities class) to do this.
        // If there are multiple tags at the same priority level we give them the same priority
        Priorities priorities = new Priorities(actor);
        Map<QID, Integer> priorityMap = new HashMap<>();
        Set<QID> remainingTags = actor.getActions().stream().map(Action::getTag).collect(Collectors.toSet());
        int priority = actor.getActions().size() - 1; // This is the highest possible priority
        while (!remainingTags.isEmpty()) {
            Set<QID> prioritizedTags = priorities.getPrioritized(null, remainingTags);
            priority = priority - (prioritizedTags.size() - 1);
            for (QID qid : prioritizedTags) {
                priorityMap.put(qid, priority);
            }
            remainingTags.removeAll(prioritizedTags);
            priority--;
        }

        for (LocalVarDecl decl : actor.getVarDecls()) {
            statements().emitStateVarDecl(decl);
        }

        for (Action action : actor.getActions()) {
            genAction(action, priorityMap);
        }

        ssaValueNumberingStack().blockDone();
    }

    /**
     * Emits the MLIR code for a single CAL action within an actor.
     * <p>
     * This method generates the body of a `cal.action` block, including its guards, input patterns,
     * local variable declarations, body statements, and output expressions.
     * <p>
     * Key tasks performed:
     * <ul>
     *   <li>Initializes a new SSA block for the action.</li>
     *   <li>Extracts the action tag and emits a new `cal.action` block with an optional priority.</li>
     *   <li>Emits guard expressions via `genGuard`, which produce predicate conditions for action firing.</li>
     *   <li>Processes input patterns using `genInputPattern`, generating FIFO reads and bindings.</li>
     *   <li>Declares action-local variables.</li>
     *   <li>Emits the body of the action (typically a sequence of statements).</li>
     *   <li>Handles output expressions through `genOutputExpression`, pushing values to output ports.</li>
     *   <li>Finalizes the action block and closes the SSA block scope.</li>
     * </ul>
     * <p>
     * Assumptions & Notes:
     * <ul>
     *   <li>Action guards, inputs, and outputs are optional but when present must conform to CAL semantics.</li>
     *   <li>Repeat expressions in inputs/outputs must be statically evaluable at compile time.</li>
     *   <li>SSA variables for bindings and temporary values are handled through `ssaValueNumberingStack()`.</li>
     * </ul>
     * <p>
     * Example output (simplified):
     * <pre>
     * cal.action "add" priority=0 {
     *     // Guard: Start
     *     cal.predicate {
     *         cal.predicate_result %cond : i1
     *     }
     *     // Guard: End
     *     // Input Pattern: Start
     *     %x = fifo.pop(%a: !fifo.output_port<i32>) : i32
     *     // Input Pattern: End
     *     // Action Local Variable Decl: Start
     *     %sum = arith.addi %x, %y : i32
     *     // Action Local Variable Decl: End
     *     // Output Expression: Start
     *     fifo.push(%sum: !fifo.input_port<i32>, %sum : i32)
     *     // Output Expression: End
     * }
     * </pre>
     *
     * @param action      The CAL action definition to be emitted as a `cal.action` MLIR block.
     * @param priorityMap
     */
    default void genAction(Action action, Map<QID, Integer> priorityMap) {
        ssaValueNumberingStack().newBlock();
        String actionTag = action.getTag().toString();

        String priority = "priority=" + priorityMap.get(action.getTag());
        emitter().emit("// Generation action: %s", actionTag);

        emitter().emit("cal.action \"%s\" %s {", actionTag, priority);
        emitter().increaseIndentation();

        for (Expression guard : action.getGuards()) {
            emitter().emit("// Guard: Start");
            genGuard(guard);
            emitter().emit("// Guard: End");
        }

        for (InputPattern inputPattern : action.getInputPatterns()) {
            emitter().emit("// Input Pattern: Start");
            genInputPattern(inputPattern);
            emitter().emit("// Input Pattern: End");
        }

        for (LocalVarDecl decl : action.getVarDecls()) {
            emitter().emit("// Action Local Variable Decl: Start");
            statements().emitVarDecl(decl);
            emitter().emit("// Action Local Variable Decl: End");
        }

        for (Statement stmt : action.getBody()) {
            statements().execute(stmt);
        }

        for (OutputExpression outputExpression : action.getOutputExpressions()) {
            emitter().emit("// Output Expression: Start");
            genOutputExpression(outputExpression);
            emitter().emit("// Output Expression: End");
        }

        emitter().decreaseIndentation();
        emitter().emit("}");

        ssaValueNumberingStack().blockDone();
    }

    /**
     * Emits MLIR code to handle output expressions in an actor's action.
     * <p>
     * This function supports two forms of output expressions:
     * <p>
     * 1. Repeated Output: For syntax like {@code Out:[t] repeat x}, it generates code that loads multiple values
     * from a memory reference (memref) and pushes them sequentially to the FIFO associated with the specified port.
     * This case requires exactly one expression and a repeat count that can be evaluated at compile time.
     * <p>
     * 2. Fixed Output List: For syntax like {@code Out:[t1, t2, ...]}, it evaluates each expression individually,
     * casts them to the appropriate port type if necessary, and emits code to push the values to the FIFO.
     * <p>
     * The method enforces constraints consistent with the CAL actor semantics and throws runtime exceptions
     * if misused (e.g., repeat used with multiple expressions, or repeat count not computable at compile time).
     *
     * @param outputExpression The output expression object representing the port, optional repeat count,
     *                         and the list of expressions to be emitted.
     * @throws RuntimeException If the output expression violates CAL semantics (e.g., invalid repeat usage).
     */
    default void genOutputExpression(OutputExpression outputExpression) {
        String portName = outputExpression.getPort().getName();
        Type portType = types().portType(outputExpression.getPort());
        String portTypeStr = typeseval().type(portType);

        List<Expression> expressions = outputExpression.getExpressions();
        String instanceName = backend().instancebox().get().getInstanceName();

        // Case 1: Out:[t] repeat x
        if (outputExpression.getRepeatExpr() != null) {
            if (expressions.size() != 1) {
                throw new RuntimeException("Action in \"" + instanceName + "\" actor contains a repeat expression and" +
                        " multiple matches. This is not allowed.");
            }

            OptionalLong repeatValueOpt = backend().constants().intValue(outputExpression.getRepeatExpr());
            if (repeatValueOpt.isEmpty()) {
                throw new RuntimeException("Value for repeat expression could not be evaluated at compile time.");
            }

            Expression expr = outputExpression.getExpressions().get(0);
            String memrefSSA = expressioneval().evaluate(expr);
            Type memrefType = types().type(expr);
            String memrefTypeStr = typeseval().type(memrefType);
            long repeatCount = repeatValueOpt.getAsLong();
            for (int i = 0; i < repeatCount; i++) {
                String constSSA = ssaValueNumberingStack().getNewTempVar();
                String ssaToPush = ssaValueNumberingStack().getNewTempVar();
                emitter().emit("%%%s = arith.constant %d : index", constSSA, i);
                emitter().emit("%%%s = memref.load %%%s[%%%s] : %s", ssaToPush, memrefSSA, constSSA, memrefTypeStr);
                emitter().emit("fifo.push(%%%s: !fifo.input_port<%s>, %%%s: %s)", portName, portTypeStr, ssaToPush,
                        portTypeStr);
            }

        } else {
            // Case 2: Out:[t1, t2, ...]
            for (Expression expr : expressions) {
                String tokenSSAName = expressioneval().evaluate(expr);
                Type exprType = types().type(expr);
                String castTokenSSA = typeseval().castType(exprType, portType, tokenSSAName);

                emitter().emit("fifo.push(%%%s: !fifo.input_port<%s>, %%%s: %s)", portName, portTypeStr, castTokenSSA
                        , portTypeStr);
            }
        }
    }

    /**
     * Emits MLIR code to handle input patterns for an actor's action system.
     * <p>
     * This function supports two cases:
     * <p>
     * 1. Repeated input pattern: If a repeat expression is specified, it allocates a memory
     * reference (memref) to store multiple values popped from the input FIFO and indexed
     * by the repeat count. (Eg: In:[t] repeat x)
     * <p>
     * 2. Fixed input pattern: If no repeat expression is present, it emits code to pop each
     * individual value from the input FIFO corresponding to the declared input variables.
     * (Eg: In:[t1, t2, ...])
     *
     * @param inputPattern The input pattern containing port information, repeat expression (if any),
     *                     and a list of input matches (variable declarations).
     * @throws RuntimeException if a repeat expression exists with multiple matches or if the repeat
     *                          expression cannot be evaluated at compile time.
     */
    default void genInputPattern(InputPattern inputPattern) {
        String portName = inputPattern.getPort().getName();

        List<Match> matches = inputPattern.getMatches();
        String instanceName = backend().instancebox().get().getInstanceName();

        // Case 1: In:[t] repeat x
        if (inputPattern.getRepeatExpr() != null) {
            if (matches.size() != 1) {
                throw new RuntimeException("Action in \"" + instanceName + "\" actor contains a repeat expression and" +
                        " multiple matches. This is not allowed.");
            }

            OptionalLong repeatValueOpt = backend().constants().intValue(inputPattern.getRepeatExpr());
            if (repeatValueOpt.isEmpty()) {
                throw new RuntimeException("Value for repeat expression could not be evaluated at compile time.");
            }

            InputVarDecl decl = matches.get(0).getDeclaration();
            String declName = backend().variables().declarationName(decl);
            String memrefName = ssaValueNumberingStack().getVarToBeAssignedTo(declName);

            Type listType = types().declaredType(decl);
            String listTypeStr = typeseval().type(listType);
            String innerTypeStr = typeseval().type(typeseval().innerType(listType));

            emitter().emit("%%%s = memref.alloca() : %s", memrefName, listTypeStr);

            long repeatCount = repeatValueOpt.getAsLong();
            for (int i = 0; i < repeatCount; i++) {
                String constSSA = ssaValueNumberingStack().getNewTempVar();
                String popSSA = ssaValueNumberingStack().getNewTempVar();

                emitter().emit("%%%s = arith.constant %d : index", constSSA, i);
                emitter().emit("%%%s = fifo.pop(%%%s: !fifo.output_port<%s>) : %s", popSSA, portName, innerTypeStr,
                        innerTypeStr);
                emitter().emit("memref.store %%%s, %%%s[%%%s] : %s", popSSA, memrefName, constSSA, listTypeStr);
            }

        } else {
            // Case 2: In:[t1, t2, ...]
            for (Match match : matches) {
                InputVarDecl decl = match.getDeclaration();
                String declName = backend().variables().declarationName(decl);
                String ssaName = ssaValueNumberingStack().getVarToBeAssignedTo(declName);

                Type type = types().declaredType(decl);
                String typeStr = typeseval().type(type);

                emitter().emit("%%%s = fifo.pop(%%%s: !fifo.output_port<%s>) : %s", ssaName, portName, typeStr,
                        typeStr);
            }
        }
    }

    default void genGuard(Expression guard) {
        emitter().emit("cal.predicate {");
        emitter().increaseIndentation();
        String evalSSA = expressioneval().evaluate(guard);
        emitter().emit("cal.predicate_result %%%s : i1", evalSSA);
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    /*
     * Callables are things like CAL functions, procedures or lambdas that can be called from a statement
     */
    void genCallables(String instanceName, Entity entity);


    default void genCallables(String instanceName, ActorMachine am) {
        boolean hasCallables = false;

        for (Scope scope : am.getScopes()) {
            if (scope.isPersistent()) {
                for (VarDecl decl : scope.getDeclarations()) {
                    if (decl.getValue() != null) {
                        Expression expr = decl.getValue();
                        if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                            if (!hasCallables) {
                                hasCallables = true;
                                emitter().emit("// -- Callables");
                            }
                            backend().callablesInActor().callableDefinition(instanceName, expr);
                            emitter().emitNewLine();
                        }
                    }
                }
            }
        }
    }
}
