package ch.epfl.vlsc.raft.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.NetworkToDot;
import ch.epfl.vlsc.platformutils.utils.Box;
import ch.epfl.vlsc.raft.backend.emitters.*;
import org.multij.Binding;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.ActorMachineScopes;
import se.lth.cs.tycho.attribute.Closures;
import se.lth.cs.tycho.attribute.ConstantEvaluator;
import se.lth.cs.tycho.attribute.FreeVariables;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Ports;
import se.lth.cs.tycho.attribute.ScopeDependencies;
import se.lth.cs.tycho.attribute.TupleAnnotations;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.attribute.VariableDeclarations;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.UniqueNumbers;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.phase.TreeShadow;

import static org.multij.BindingKind.INJECTED;
import static org.multij.BindingKind.LAZY;

@Module
public interface RaftBackend {

    // ------------------------------------------------------------------------
    // -- Compilation

    // -- Compilation Task
    @Binding(INJECTED)
    CompilationTask task();

    // -- Compilation Context
    @Binding(INJECTED)
    Context context();

    // ------------------------------------------------------------------------
    // -- Compiler Attributes

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

    @Binding(LAZY)
    default TupleAnnotations tupleAnnotations() {
        return task().getModule(TupleAnnotations.key);
    }

    // ------------------------------------------------------------------------
    // -- Emitter
    @Binding(LAZY)
    default Emitter emitter() {
        return new Emitter();
    }

    @Binding(LAZY)
    default Emitter clEmitter() {
        return new Emitter();
    }

    // ------------------------------------------------------------------------
    // -- Boxes

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

    // ------------------------------------------------------------------------
    // -- Auxilary Modules

    // -- Network to DOT
    @Binding(LAZY)
    default NetworkToDot networkToDot() {
        return MultiJ.from(NetworkToDot.class)
                .bind("emitter").to(emitter())
                .bind("globalNames").to(globalnames())
                .bind("constants").to(constants())
                .instance();
    }

    // ------------------------------------------------------------------------
    // -- Backend related Emitters

    @Binding(LAZY)
    default Instances instance() {
        return MultiJ.from(Instances.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default TypesEvaluator typeseval() {
        return MultiJ.from(TypesEvaluator.class)
                .bind("types").to(types())
                .bind("alias").to(alias())
                .instance();
    }

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

    default PatternMatching patmat() {
        return MultiJ.from(PatternMatching.class).bind("backend").to(this).instance();
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
    default DefaultValues defaultValues() {
        return MultiJ.from(DefaultValues.class).bind("typeseval").to(typeseval()).instance();
    }

    @Binding(LAZY)
    default Declarations declarations() {
        return MultiJ.from(Declarations.class)
                .bind("backend").to(this)
                .bind("types").to(types())
                .instance();
    }

    @Binding(LAZY)
    default Variables variables() {
        return MultiJ.from(Variables.class)
                .bind("uniqueNumbers").to(uniqueNumbers())
                .bind("tree").to(tree())
                .bind("varDecls").to(varDecls())
                .bind("closures").to(closures())
                .instance();
    }

    @Binding(LAZY)
    default Expressions expressions() {
        return MultiJ.from(Expressions.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Statements statements() {
        return MultiJ.from(Statements.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Callables callables() {
        return MultiJ.from(Callables.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Channels channels() {
        return MultiJ.from(Channels.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Controllers controllers() {
        return MultiJ.from(Controllers.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Main main() {
        return MultiJ.from(Main.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Globals globals() {
        return MultiJ.from(Globals.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default CMakeLists cmakelists() {
        return MultiJ.from(CMakeLists.class).bind("backend").to(this).instance();
    }

    // ------------------------------------------------------------------------
    // -- Emitters for includes

    default void includeUser(String h) {
        emitter().emit("#include \"%s\"", h);
    }

    default void includeSystem(String h) {
        emitter().emit("#include <%s>", h);
    }

}
