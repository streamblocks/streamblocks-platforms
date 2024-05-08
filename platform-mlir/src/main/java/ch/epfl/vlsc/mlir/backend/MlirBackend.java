package ch.epfl.vlsc.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.Box;
import ch.epfl.vlsc.platformutils.utils.StackSSA;
import org.multij.Binding;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.*;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.UniqueNumbers;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.phase.TreeShadow;

import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;

import static org.multij.BindingKind.INJECTED;
import static org.multij.BindingKind.LAZY;

@Module
public interface MlirBackend {
    // -- Compilation Task
    @Binding(INJECTED)
    CompilationTask task();

    // -- Compilation Context
    @Binding(INJECTED)
    Context context();

    // -- Globals names
    @Binding(LAZY)
    default GlobalNames globalnames() {
        return task().getModule(GlobalNames.key);
    }

    // -- Emitter
    @Binding(LAZY)
    default Emitter emitter() {
        return new Emitter();
    }

    // -- Channels Utils
    @Binding(LAZY)
    default ChannelsUtils channelsutils() {
        return MultiJ.from(ChannelsUtils.class).bind("backend").to(this).instance();
    }

    // -- Variables
    @Binding(LAZY)
    default Variables variables() {
        return MultiJ.from(Variables.class).bind("backend").to(this).instance();
    }

    // -- Declarations
    @Binding(LAZY)
    default Declarations declarations() {
        return MultiJ.from(Declarations.class).bind("backend").to(this).instance();
    }

    // -- Expression evaluator
    @Binding(LAZY)
    default ExpressionEvaluator expressionEval() {
        return MultiJ.from(ExpressionEvaluator.class).bind("backend").to(this).instance();
    }

    // -- Types
    @Binding(LAZY)
    default Types types() {
        return task().getModule(Types.key);
    }

    // -- Statements
    @Binding(LAZY)
    default Statements statements() {
        return MultiJ.from(Statements.class).bind("backend").to(this).instance();
    }

    // -- TypesEvaluator
    @Binding(LAZY)
    default TypesEvaluator typeseval() {
        return MultiJ.from(TypesEvaluator.class).bind("backend").to(this).instance();
    }

    // -- Entity Box
    @Binding(LAZY)
    default Box<Entity> entitybox() {
        return Box.empty();
    }

    // -- Instance Box
    @Binding(LAZY)
    default Box<Instance> instancebox() {
        return Box.empty();
    }

    // -- A box storing the number of times a variable name is used
    // TODO: explain more on this
    @Binding(LAZY)
    default StackSSA ssaValueNumberingStack() {
        return new StackSSA();
    }

    // -- Constant Evaluator
    @Binding(LAZY)
    default ConstantEvaluator constants() {
        return task().getModule(ConstantEvaluator.key);
    }

    // -- IR Tree Shadow
    @Binding(LAZY)
    default TreeShadow tree() {
        return task().getModule(TreeShadow.key);
    }

    // -- Unique Numbers
    @Binding(LAZY)
    default UniqueNumbers uniqueNumbers() {
        return context().getUniqueNumbers();
    }

    // -- Variable Declarations
    @Binding(LAZY)
    default VariableDeclarations varDecls() {
        return task().getModule(VariableDeclarations.key);
    }

    // -- Callables
    @Binding(LAZY)
    default Callables callables() {
        return MultiJ.from(Callables.class).bind("backend").to(this).instance();
    }

    // -- LValues
    @Binding(LAZY)
    default LValues lvalues() {
        return MultiJ.from(LValues.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default DefaultValues defaultValues() {
        return MultiJ.from(DefaultValues.class).bind("backend").to(this).instance();
    }

    // -- Free variables
    @Binding(LAZY)
    default FreeVariables freeVariables() {
        return task().getModule(FreeVariables.key);
    }

    @Binding(LAZY)
    default Algebraic algebraic() {
        return MultiJ.from(Algebraic.class).bind("backend").to(this).instance();
    }

    /*@Binding(LAZY)
    default ch.epfl.vlsc.sw.backend.SizeOf sizeof() {
        return MultiJ.from(SizeOf.class).bind("typeseval").to(typeseval()).instance();
    }*/

    @Binding(LAZY)
    default CallablesInActors callablesInActor() {
        return MultiJ.from(CallablesInActors.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Main main() {
        return MultiJ.from(Main.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default BuildSystem buildSystem(){
        return MultiJ.from(BuildSystem.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default TestBenchGenerator testBenchGenerator(){
        return MultiJ.from(TestBenchGenerator.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default Instances instance() {
        return MultiJ.from(Instances.class).bind("backend").to(this).instance();
    }
}
