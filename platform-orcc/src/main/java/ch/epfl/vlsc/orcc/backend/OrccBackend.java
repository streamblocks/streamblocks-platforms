package ch.epfl.vlsc.orcc.backend;

import ch.epfl.vlsc.orcc.backend.emitters.*;
import ch.epfl.vlsc.platformutils.DefaultValues;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.NetworkToDot;
import ch.epfl.vlsc.platformutils.utils.Box;
import org.multij.Binding;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.*;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.UniqueNumbers;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.op.Binary;
import se.lth.cs.tycho.meta.interp.op.Unary;
import se.lth.cs.tycho.meta.interp.value.util.Convert;
import se.lth.cs.tycho.phase.TreeShadow;

import static org.multij.BindingKind.INJECTED;
import static org.multij.BindingKind.LAZY;

@Module
public interface OrccBackend {

    // -- Compilation Task
    @Binding(INJECTED)
    CompilationTask task();

    // -- Compilation Context
    @Binding(INJECTED)
    Context context();

    // -- Instance Box
    @Binding(LAZY)
    default Box<Instance> instancebox() {
        return Box.empty();
    }

    // -- Entity Box
    @Binding(LAZY)
    default Box<Entity> entitybox() {
        return Box.empty();
    }

    @Binding(LAZY)
    default Box<Boolean> alignedBox() {
        return Box.empty();
    }

    @Binding(LAZY)
    default Box<Boolean> alwaysAlignedBox() {
        return Box.empty();
    }

    @Binding(LAZY)
    default Box<Boolean> writeBox() {
        return Box.empty();
    }

    // -- Globals names
    @Binding(LAZY)
    default GlobalNames globalnames() {
        return task().getModule(GlobalNames.key);
    }

    // -- Types
    @Binding(LAZY)
    default Types types() {
        return task().getModule(Types.key);
    }

    // -- Unique Numbers
    @Binding(LAZY)
    default UniqueNumbers uniqueNumbers() {
        return context().getUniqueNumbers();
    }

    // -- Constant Evaluator
    @Binding(LAZY)
    default ConstantEvaluator constants() {
        return task().getModule(ConstantEvaluator.key);
    }

    // -- Variable Declarations
    @Binding(LAZY)
    default VariableDeclarations varDecls() {
        return task().getModule(VariableDeclarations.key);
    }

    // -- IR Tree Shadow
    @Binding(LAZY)
    default TreeShadow tree() {
        return task().getModule(TreeShadow.key);
    }

    // -- Ports
    @Binding(LAZY)
    default Ports ports() {
        return task().getModule(Ports.key);
    }

    // -- Actor Machine scopes
    @Binding(LAZY)
    default ActorMachineScopes scopes() {
        return task().getModule(ActorMachineScopes.key);
    }

    // -- Closures
    @Binding(LAZY)
    default Closures closures() {
        return task().getModule(Closures.key);
    }

    @Binding(LAZY)
    default TupleAnnotations tupleAnnotations() {
        return task().getModule(TupleAnnotations.key);
    }

    // -- Free variables
    @Binding(LAZY)
    default FreeVariables freeVariables() {
        return task().getModule(FreeVariables.key);
    }

    // -- Scope dependencies
    @Binding(LAZY)
    default ScopeDependencies scopeDependencies() {
        return task().getModule(ScopeDependencies.key);
    }

    // -- Emitter
    @Binding(LAZY)
    default Emitter emitter() {
        return new Emitter();
    }

    // -- Network to DOT
    @Binding(LAZY)
    default NetworkToDot networkToDot() {
        return MultiJ.from(NetworkToDot.class)
                .bind("emitter").to(emitter())
                .bind("globalNames").to(globalnames())
                .bind("constants").to(constants())
                .instance();
    }

    @Binding(LAZY)
    default Trackable memoryStack() {
        return MultiJ.from(Trackable.class).bind("backend").to(this).instance();
    }

    // -- Declarations
    @Binding(LAZY)
    default Declarations declarations() {
        return MultiJ.from(Declarations.class).bind("backend").to(this).instance();
    }

    // -- Variables
    @Binding(LAZY)
    default Variables variables() {
        return MultiJ.from(Variables.class).bind("backend").to(this).instance();
    }

    // -- Types evaluator
    @Binding(LAZY)
    default TypesEvaluator typeseval() {
        return MultiJ.from(TypesEvaluator.class).bind("types").to(types()).instance();
    }

    // -- Expression emitter
    @Binding(LAZY)
    default ExpressionEvaluator expressionEval() {
        return MultiJ.from(ExpressionEvaluator.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default LValues lvalues() {
        return MultiJ.from(LValues.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Statements statements() {
        return MultiJ.from(Statements.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Controller controller() {
        return MultiJ.from(Controller.class).bind("backend").to(this).bind("ports").to(task().getModule(Ports.key)).instance();
    }

    // -- Callables
    @Binding(LAZY)
    default Callables callables() {
        return MultiJ.from(Callables.class).bind("backend").to(this).instance();
    }

    // -- Callables in Actor
    @Binding(LAZY)
    default CallablesInActors callablesInActors() {
        return MultiJ.from(CallablesInActors.class).bind("backend").to(this).instance();
    }

    // -- Default Values
    @Binding(LAZY)
    default DefaultValues defaultValues() {
        return MultiJ.from(DefaultValues.class).instance();
    }

    // -- Channel utilities
    @Binding(LAZY)
    default ChannelUtils channelUtils() {
        return MultiJ.from(ChannelUtils.class).bind("backend").to(this).instance();
    }

    // ------------------------------------------------------------------------
    // -- Types

    @Binding(LAZY)
    default Alias alias() {
        return MultiJ.from(Alias.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Algebraic algebraic() {
        return MultiJ.from(Algebraic.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Tuples tuples() {
        return MultiJ.from(Tuples.class).bind("backend").to(this).instance();
    }


    @Binding(LAZY)
    default SizeOf sizeof() {
        return MultiJ.from(SizeOf.class).bind("typeseval").to(typeseval()).instance();
    }

    @Binding(LAZY)
    default Free free() {
        return MultiJ.from(Free.class).bind("typeseval").to(typeseval()).instance();
    }

    @Binding(LAZY)
    default Serialization serialization() {
        return MultiJ.from(Serialization.class)
                .bind("typeseval").to(typeseval())
                .bind("emitter").to(emitter())
                .bind("sizeof").to(sizeof())
                .instance();
    }

    @Binding(LAZY)
    default Strings strings() {
        return MultiJ.from(Strings.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Interpreter interpreter() {
        return MultiJ.from(Interpreter.class)
                .bind("variables").to(task().getModule(VariableDeclarations.key))
                .bind("types").to(task().getModule(TypeScopes.key))
                .bind("unary").to(MultiJ.from(Unary.class).instance())
                .bind("binary").to(MultiJ.from(Binary.class).instance())
                .instance();
    }

    @Binding(LAZY)
    default Convert converter() {
        return MultiJ.from(Convert.class)
                .instance();
    }


    // ------------------------------------------------------------------------
    // -- Generators

    // -- Instance generator
    @Binding(LAZY)
    default Instances instance() {
        return MultiJ.from(Instances.class).bind("backend").to(this).instance();
    }

    // -- Main generator
    @Binding(LAZY)
    default Main main() {
        return MultiJ.from(Main.class).bind("backend").to(this).instance();
    }

    // -- Globals generator
    @Binding(LAZY)
    default Globals globals() {
        return MultiJ.from(Globals.class).bind("backend").to(this).instance();
    }


    // -- CMakeLists generator
    @Binding(LAZY)
    default CMakeLists cmakelists() {
        return MultiJ.from(CMakeLists.class).bind("backend").to(this).instance();
    }


    // -- Utils
    default QID taskIdentifier() {
        return task().getIdentifier().getButLast();
    }

    /**
     * Get the QID of the instance for the Multicore platform
     *
     * @param instanceName
     * @param delimiter
     * @return
     */
    default String instaceQID(String instanceName, String delimiter) {
        return String.join(delimiter, taskIdentifier().parts()) + "_" + instanceName;
    }

    default void includeUser(String h) {
        emitter().emit("#include \"%s\"", h);
    }

    default void includeSystem(String h) {
        emitter().emit("#include <%s>", h);
    }

}
