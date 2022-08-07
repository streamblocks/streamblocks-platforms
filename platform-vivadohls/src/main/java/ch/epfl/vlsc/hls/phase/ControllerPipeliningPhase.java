package ch.epfl.vlsc.hls.phase;

import ch.epfl.vlsc.hls.backend.directives.Directives;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.ConstantEvaluator;
import se.lth.cs.tycho.attribute.EntityDeclarations;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.VariableDeclarations;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.compiler.Transformations;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.InputVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.cal.Action;

import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.entity.cal.InputPattern;
import se.lth.cs.tycho.ir.entity.cal.Match;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;


import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ControllerPipeliningPhase implements Phase {


    @Override
    public String getDescription() {
        return "Validate controller pipelining annotations on and remove them if they can not " +
                "be pipelined with the current controllers";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {



        return Transformations.transformEntityDecls(task, decl -> {
            // check whether the declared entity is an actor machine
            boolean hasNoActorMachine = Annotation.hasAnnotationWithName("no_actor_machine",
                    decl.getEntity().getAnnotations());

            if (decl.getEntity() instanceof CalActor && !hasNoActorMachine) {
                CalActor transformed = KeepOrRemovePipelineAnnotations(
                        (CalActor) decl.getEntity(),
                        context.getReporter(),
                        decl,
                        task
                );
                return decl.withEntity(transformed);
            } else {
                return decl;
            }
        });

    }

    private CalActor KeepOrRemovePipelineAnnotations(CalActor actor,
                                                     Reporter reporter,
                                                     GlobalEntityDecl entityDecl,
                                                     CompilationTask task) {
        boolean hasPipelineAnnotation = actor.getAnnotations().stream().anyMatch(annotation ->
                Directives.directive(annotation.getName()) == Directives.PIPELINE);
        if (!hasPipelineAnnotation) {
            return actor;
        }

        // has pipeline annotation, so make sure that all no action has a "peek" guard condition




        List<ExprInput> peekGuardExpressions = new ArrayList<>();

        // traverse the guard expression and collect all the ExprInput expression (peek)
        Consumer<Expression> checker = node ->{
            if (node instanceof ExprInput)
                peekGuardExpressions.add((ExprInput) node);
        };

        VariableDeclarations declarations = task.getModule(VariableDeclarations.key);
        FindSourceUnit sourceUnitFinder = MultiJ.from(FindSourceUnit.class).bind("tree")
                .to(task.getModule(TreeShadow.key)).instance();


        actor.getActions().forEach(action -> {
            ImmutableList<ExprVariable> refs = checkActionGuards(action, declarations);

            if (!refs.isEmpty()) {

                reporter.report(new Diagnostic(
                        Diagnostic.Kind.WARNING,
                        "Removing pipeline annotations for actor " + entityDecl.getOriginalName() +
                                " due to input pattern dependent guard condition on action " + action.getTag() + "\n",
                        sourceUnitFinder.sourceUnit(entityDecl),
                        entityDecl
                ));
                Consumer<ExprVariable> warn = expr -> {
                    reporter.report(
                            new Diagnostic(
                                    Diagnostic.Kind.WARNING,
                                    "Guard condition depends on input value " + expr.toString(),
                                    sourceUnitFinder.sourceUnit(expr),
                                    expr
                            )
                    );
                };
                refs.forEach(warn);
            }
        });

        if (peekGuardExpressions.isEmpty()) {
           return actor;
        }

        ImmutableList<Annotation> annotations = actor.getAnnotations().stream().filter(
                annotation ->
                        Directives.directive(annotation.getName()) != Directives.PIPELINE
        ).collect(ImmutableList.collector());

//        Optional<QID> actorQid = globalNames.globalName()

        return actor.withAnnotations(annotations);


    }

    private ImmutableList<ExprVariable> checkActionGuards(Action action, VariableDeclarations declarations) {

        // a list of all input variables declared on the input patterns of the action
        ImmutableList<InputVarDecl> inputVars =
                action.getInputPatterns().stream().flatMap(pattern -> pattern.getMatches().stream())
                        .map(Match::getDeclaration).collect(ImmutableList.collector());

        CollectExprVars visitor = MultiJ.from(CollectExprVars.class)
                .bind("declarations")
                .to(declarations)
                .instance();

        // visit all guard expression and collect variable references to the input vars
        action.getGuards().forEach(visitor::visit);

        // visit all local var decls and collect references to input vars
        action.getVarDecls().forEach(v -> v.forEachChild(visitor::visit));

        ImmutableList<ExprVariable> inputVarRefs =
                visitor.collection().stream().filter(expr ->
                    inputVars.stream().anyMatch(
                            decl -> declarations.declaration(expr).equals(decl)
                )).collect(ImmutableList.collector());

        return inputVarRefs;

    }


    /**
     * Visitor interface for collecting all the ExprVariables in an Expression
     */
    @Module
    public interface CollectExprVars {

        @Binding(BindingKind.INJECTED)
        VariableDeclarations declarations();

        @Binding(BindingKind.LAZY)
        default List<ExprVariable> collection() {
            return new ArrayList<>();
        }

        default void visit(IRNode node) {}

        default void visit(Expression expr) {
            expr.forEachChild(this::visit);
        }

        default void visit(ExprVariable expr) {
            collection().add(expr);
        }

    }

    @Module
    public interface FindSourceUnit {
        @Binding(BindingKind.INJECTED)
        TreeShadow tree();

        default SourceUnit sourceUnit(IRNode node) {
            return sourceUnit(tree().parent(node));
        }

        default SourceUnit sourceUnit(SourceUnit unit) {
            return unit;
        }
    }


}
