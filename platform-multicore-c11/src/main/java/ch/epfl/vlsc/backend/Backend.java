package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.QID;

import static org.multij.BindingKind.INJECTED;
import static org.multij.BindingKind.LAZY;

@Module
public interface Backend {
    // -- Attributes
    @Binding(INJECTED)
    CompilationTask task();

    @Binding(INJECTED)
    Context context();

    @Binding(LAZY) default Types types() {
        return task().getModule(Types.key);
    }

    // -- Emiter
    @Binding(LAZY)
    default Emitter emitter() {
        return new Emitter();
    }

    // -- Global names
    @Binding(LAZY)
    default GlobalNames globalnames() {
        return task().getModule(GlobalNames.key);
    }

    // -- CMakeList generator
    @Binding(LAZY)
    default CMakeLists cmakelists() {
        return MultiJ.from(CMakeLists.class).bind("backend").to(this).instance();
    }

    // -- Expression evaluator
    @Binding(LAZY)
    default ExpressionEvaluator expressioneval() {
        return MultiJ.from(ExpressionEvaluator.class).bind("backend").to(this).instance();
    }

    // -- TypesEvaluator
    @Binding(LAZY)
    default TypesEvaluator typeseval(){
        return MultiJ.from(TypesEvaluator.class).bind("backend").to(this).instance();
    }

    // -- Declarations
    default Declarations declarations(){
        return MultiJ.from(Declarations.class).bind("backend").to(this).instance();
    }

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

}
