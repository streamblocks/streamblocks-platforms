package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.ch.epfl.vlsc.backend.utils.Box;
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
import se.lth.cs.tycho.phase.TreeShadow;
import sun.jvm.hotspot.debugger.cdbg.basic.LazyBlockSym;

import static org.multij.BindingKind.INJECTED;
import static org.multij.BindingKind.LAZY;

@Module
public interface VivadoHLSBackend {

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

    // -- Emitter
    @Binding(LAZY)
    default Emitter emitter() {
        return new Emitter();
    }

    // -- Types evaluator
    @Binding(LAZY)
    default TypesEvaluator typeseval() {return MultiJ.from(TypesEvaluator.class).bind("backend").to(this).instance();}

    // -- Declarations
    @Binding(LAZY)
    default Declarations declarations() {return MultiJ.from(Declarations.class).bind("backend").to(this).instance();}

    // -- Variables
    @Binding(LAZY)
    default Variables variables() {return MultiJ.from(Variables.class).bind("backend").to(this).instance();}

    // -- ChannelUtils
    @Binding(LAZY)
    default LValues lvalues() {return MultiJ.from(LValues.class).bind("backend").to(this).instance();}

    // -- Expression Evaluator
    @Binding(LAZY)
    default ExpressionEvaluator expressioneval() {return MultiJ.from(ExpressionEvaluator.class).bind("backend").to(this).instance();}

    // -- Statements
    @Binding(LAZY)
    default Statements statements() {return MultiJ.from(Statements.class).bind("backend").to(this).instance();}

    // -- Callables
    @Binding(LAZY)
    default CallablesInActors callables() {return MultiJ.from(CallablesInActors.class).bind("backend").to(this).instance();}

    // -- ChannelUtils
    @Binding(LAZY)
    default ChannelsUtils channelsutils() {return MultiJ.from(ChannelsUtils.class).bind("backend").to(this).instance();}

    // -- Instance generator
    @Binding(LAZY)
    default Instances instance() {
        return MultiJ.from(Instances.class).bind("backend").to(this).instance();
    }

    // -- Globals
    @Binding(LAZY)
    default Globals globals() {
        return MultiJ.from(Globals.class).bind("backend").to(this).instance();
    }



    // -- Utils
    default QID taskIdentifier() {
        return task().getIdentifier().getButLast();
    }

    /**
     * Get the QID of the instance for the C11 platform
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
