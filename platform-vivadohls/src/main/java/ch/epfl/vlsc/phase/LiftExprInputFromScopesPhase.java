package ch.epfl.vlsc.phase;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.ScopeDependencies;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.attribute.VariableDeclarations;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.decoration.TypeToTypeExpr;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.Condition;
import se.lth.cs.tycho.ir.entity.am.PredicateCondition;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LiftExprInputFromScopesPhase implements Phase {
    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {


        Transformation t = MultiJ.from(Transformation.class)
                .bind("scopes").to(task.getModule(ScopeDependencies.key))
                .bind("varDecls").to(task.getModule(VariableDeclarations.key))
                .bind("types").to(task.getModule(Types.key))
                .instance();
        return task.transformChildren(t);
    }


    @Module
    interface Transformation extends IRNode.Transformation {
        @Binding(BindingKind.INJECTED)
        ScopeDependencies scopes();

        @Binding(BindingKind.INJECTED)
        VariableDeclarations varDecls();

        @Binding(BindingKind.INJECTED)
        Types types();

        @Override
        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default Scope apply(Scope scope) {
            Set<Condition> conditions = scopes().conditionsUsingScope(scope)
                    .stream()
                    .filter(c -> c instanceof PredicateCondition)
                    .collect(Collectors.toSet());

            List<LocalVarDecl> toBeRemoved = new ArrayList<>();

            for (LocalVarDecl decl : scope.getDeclarations()) {
                if (decl.getValue() != null) {
                    if (decl.getValue() instanceof ExprInput) {
                        if (conditions.isEmpty()) {
                            toBeRemoved.add(decl);
                        } else {
                            for (Condition condition : conditions) {
                                PredicateCondition p = (PredicateCondition) condition;
                                Expression expr = p.getExpression();
                                if (!visit(decl, expr)) {
                                    toBeRemoved.add(decl);
                                }
                            }
                        }

                    }
                }
            }

            if(!toBeRemoved.isEmpty()){
                ImmutableList.Builder<LocalVarDecl> newDecls = ImmutableList.builder();

                for(LocalVarDecl decl : scope.getDeclarations()){
                    if(toBeRemoved.contains(decl)){
                        Type type = types().declaredType(decl);
                        newDecls.add(decl.withValue(null).withType(TypeToTypeExpr.convert(type)));
                    }else{
                        newDecls.add(decl);
                    }
                }
                return scope.copy(newDecls.build(), scope.isPersistent());
            }

            return scope;
        }

        default boolean visit(VarDecl decl, Expression expr) {
            return false;
        }

        default boolean visit(VarDecl decl, ExprUnaryOp expr) {
            return visit(decl, expr.getOperand());
        }

        default boolean visit(VarDecl decl, ExprBinaryOp expr) {
            boolean a = visit(decl, expr.getOperands().get(0));
            boolean b = visit(decl, expr.getOperands().get(1));
            return a | b;
        }

        default boolean visit(VarDecl decl, ExprIndexer expr){
            boolean i = visit(decl, expr.getIndex());
            boolean s = visit(decl, expr.getStructure());
            return i | s;
        }

        default boolean visit(VarDecl decl, ExprVariable expr) {
            return decl.equals(varDecls().declaration(expr));
        }

        default boolean visit(VarDecl decl, ExprApplication expr) {
            for (Expression e : expr.getArgs()) {
                boolean r = visit(decl, e);
                if (r) {
                    return true;
                }
            }
            return false;
        }


    }


}
