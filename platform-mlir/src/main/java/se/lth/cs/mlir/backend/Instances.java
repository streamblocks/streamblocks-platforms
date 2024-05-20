package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.sw.ir.PartitionHandle;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.network.Instance;

import java.util.List;
import java.util.stream.Collectors;

@Module
public interface Instances {
    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

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

        String inputPortString =
                inputPortNamesTypes.stream().map(x -> "%" + x._1 + ": " + x._2).collect(Collectors.joining(","));
        String outputPortString =
                outputPortNamesTypes.stream().map(x -> "%" + x._1 + ": " + x._2).collect(Collectors.joining(","));


        // 1. Declare the actor
        emitter().emit("//-- Definition of actor class: %s", entityClass);
        emitter().emit("dfg.operator @" + entityClass);
        emitter().emit("\tinputs(%s)", inputPortString);
        emitter().emit("\toutputs(%s)", outputPortString);
        emitter().emit("{");
        emitter().increaseIndentation();

        // 2. Generate the actions of the actor
        emitter().emit("dfg.loop inputs(%s)", inputPortString);
        emitter().emit("{");
        emitter().increaseIndentation();
        genActions(entityDecl.getEntity());
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();

        // 3. Generate the callables such as functions and procedures
        genCallables(entityName, entityDecl.getEntity());

        // 4. Close the actor
        emitter().decreaseIndentation();
        emitter().emit("}");

        backend().instancebox().clear();
        backend().entitybox().clear();
    }

    void genActions(Entity entity);

    default void genActions(ActorMachine am) {
        emitter().emit("// -- Actor body");

        for (Transition trans : am.getTransitions()) {
            trans.getBody().forEach(statements()::execute);
        }

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
