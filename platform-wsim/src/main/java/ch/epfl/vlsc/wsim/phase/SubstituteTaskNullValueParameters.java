package ch.epfl.vlsc.wsim.phase;

import ch.epfl.vlsc.wsim.backend.WSimBackend;
import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprRuntimeArgument;
import ch.epfl.vlsc.wsim.phase.attributes.SourceUnitFinder;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.GlobalDeclarations;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;


public class SubstituteTaskNullValueParameters implements Phase {

    @Override
    public String getDescription() {
        return "Substitute null default values for the top level network with runt-time value expression, " +
                "useful for feeding parameters in a software runtime";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        Transformation transformation = MultiJ.from(Transformation.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .bind("sourceFinder").to(task.getModule(SourceUnitFinder.key))
                .instance();

        CompilationTask transformed = (CompilationTask) task.transformChildren(transformation);
        return transformed;

    }

    @Module
    interface Transformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        CompilationTask task();
        @Binding(BindingKind.INJECTED)
        Context context();
        @Binding(BindingKind.INJECTED)
        SourceUnitFinder sourceFinder();

        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default SourceUnit apply(SourceUnit src) {
            if (src.getTree().getQID().equals(task().getIdentifier().getButLast())) {
                return src.transformChildren(this);
            } else {
                return src;
            }
        }
        default GlobalEntityDecl apply(GlobalEntityDecl decl) {

            if (decl.getName().equals(task().getIdentifier().getLast().toString())) {

                return fixValueParameters(decl);
            } else {
                return decl;
            }
        }


        default GlobalEntityDecl fixValueParameters(GlobalEntityDecl decl) {

            ImmutableList<ParameterVarDecl> params = decl.getEntity().getValueParameters().map(p -> {
                if (p.getDefaultValue() == null) {
                    context().getReporter().report(
                            new Diagnostic(Diagnostic.Kind.INFO,
                                    "Replacing null-valued value parameter with backend-dependent " +
                                            "runtime arguments", sourceFinder().find(p), p)
                    );
                    return p.withDefaultValue(new ExprRuntimeArgument());
                } else {
                    return p.deepClone();
                }
            });

            Entity transformed = decl.getEntity().withValueParameters(params);
            return decl.withEntity(transformed);
        }
    }




}
