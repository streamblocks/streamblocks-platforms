package ch.epfl.vlsc.phases;

import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.ExprIf;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtBlock;
import se.lth.cs.tycho.ir.stmt.StmtIf;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

import java.util.ArrayList;
import java.util.List;

public class ExprToStmtAssignment implements Phase {
    @Override
    public String getDescription() {
        return "Expressions to Statements";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Transformation transformation = MultiJ.from(Transformation.class)
                .instance();

        return task.transformChildren(transformation);
    }

    @Module
    interface Transformation extends IRNode.Transformation {

        @Override
        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default IRNode apply(StmtBlock block) {

            List<Statement> statements = new ArrayList<>(block.getStatements());

            for (Statement stmt : block.getStatements()) {
                if (stmt instanceof StmtAssignment) {
                    StmtAssignment assign = (StmtAssignment) stmt;
                    if (assign.getExpression() instanceof ExprIf) {
                        ExprIf exprIf = (ExprIf) assign.getExpression();

                        StmtAssignment thenAssign = new StmtAssignment((LValue) assign.getLValue().deepClone(), exprIf.getThenExpr().deepClone());
                        StmtAssignment elseAssign = new StmtAssignment((LValue) assign.getLValue().deepClone(), exprIf.getElseExpr().deepClone());

                        StmtIf stmtIf = new StmtIf(exprIf.getCondition().deepClone(), ImmutableList.of(thenAssign), ImmutableList.of(elseAssign));
                        statements.set(statements.indexOf(stmt), stmtIf);
                    }
                }
            }

            return block.transformChildren(this).withStatements(ImmutableList.copyOf(statements));
        }
    }



}
