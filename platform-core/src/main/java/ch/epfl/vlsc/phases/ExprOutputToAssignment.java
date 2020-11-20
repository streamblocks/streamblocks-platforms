package ch.epfl.vlsc.phases;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.UniqueNumbers;
import se.lth.cs.tycho.decoration.TypeToTypeExpr;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.decl.TypeDecl;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtBlock;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.List;

public class ExprOutputToAssignment implements Phase {
    @Override
    public String getDescription() {
        return "Replaces ExprOutput expression with an ExpVar and a StmtAssignment";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Transformation transformation = MultiJ.from(Transformation.class)
                .bind("types").to(task.getModule(Types.key))
                .bind("numbers").to(context.getUniqueNumbers())
                .instance();

        return task.transformChildren(transformation);
    }

    @Module
    interface Transformation extends IRNode.Transformation {
        @Binding(BindingKind.INJECTED)
        Types types();

        @Binding(BindingKind.INJECTED)
        UniqueNumbers numbers();

        @Override
        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default IRNode apply(StmtBlock block) {

            StmtBlock vBlock = block.transformChildren(this);

            List<StmtWrite> writes = new ArrayList<>();

            List<TypeDecl> typeDeclarations = new ArrayList<>(vBlock.getTypeDecls());

            List<LocalVarDecl> declarations = new ArrayList<>(vBlock.getVarDecls());

            List<Statement> statements = new ArrayList<>(vBlock.getStatements());

            for (Statement stmt : vBlock.getStatements()) {
                if (stmt instanceof StmtWrite) {
                    StmtWrite write = (StmtWrite) stmt;
                    writes.add(write);
                }
            }

            if (writes.isEmpty()) {
                return vBlock;
            }

            for (StmtWrite write : writes) {
                ImmutableList.Builder<Expression> expressions = ImmutableList.builder();
                for (Expression expr : write.getValues()) {
                    if (!(expr instanceof ExprVariable)) {

                        Type type = types().type(expr);
                        if (type instanceof ListType) {
                            TypeExpr typeExpr = TypeToTypeExpr.convert(type);

                            String name = write.getPort().getName() + "_" + numbers().next();

                            LocalVarDecl decl = new LocalVarDecl(ImmutableList.empty(), typeExpr, name, null, false);
                            declarations.add(decl);

                            LValueVariable lValue = new LValueVariable(Variable.variable(name));
                            StmtAssignment assignment = new StmtAssignment(lValue, expr.deepClone());
                            statements.add(statements.indexOf(write), assignment);
                            expressions.add(new ExprVariable(Variable.variable(name)));
                        }else{
                            expressions.add(expr);
                        }
                    }else{
                        expressions.add(expr);
                    }
                }
                StmtWrite newWrite;
                if(write.getRepeatExpression() != null){
                    newWrite = new StmtWrite(write.getPort().clone(), expressions.build(), write.getRepeatExpression().deepClone());
                }else{
                    newWrite = new StmtWrite(write.getPort().clone(), expressions.build(), null);
                }
                statements.set(statements.indexOf(write), newWrite);
            }

            return new StmtBlock(ImmutableList.copyOf(typeDeclarations), ImmutableList.copyOf(declarations), ImmutableList.copyOf(statements));
        }

    }

}
