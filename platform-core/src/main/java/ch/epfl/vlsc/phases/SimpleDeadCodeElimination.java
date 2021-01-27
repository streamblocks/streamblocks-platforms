package ch.epfl.vlsc.phases;


import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.StmtBlock;
import se.lth.cs.tycho.ir.stmt.StmtIf;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueBool;
import se.lth.cs.tycho.meta.interp.value.ValueUndefined;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

public class SimpleDeadCodeElimination implements Phase {
    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Interpreter interpreter = task.getModule(Interpreter.key);


        Transformation transformation = MultiJ.from(Transformation.class)
                .bind("interpreter").to(interpreter)
                .instance();

        return task.transformChildren(transformation);
    }

    @Module
    interface Transformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        Interpreter interpreter();


        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default IRNode apply(StmtIf stmtIf) {
            Environment env = new Environment();
            Expression condition = stmtIf.getCondition();
            Value value = interpreter().eval(condition, env);


            if (value instanceof ValueUndefined) {
                return stmtIf;
            }

            ValueBool valueBool = (ValueBool) value;

            if (valueBool.bool()) {
                if (!stmtIf.getThenBranch().isEmpty()) {
                    return new StmtBlock(ImmutableList.empty(), ImmutableList.empty(), stmtIf.getThenBranch());
                } else {
                    return new StmtBlock(ImmutableList.empty(), ImmutableList.empty(), ImmutableList.empty());
                }
            } else {
                if (!stmtIf.getElseBranch().isEmpty()) {
                    return new StmtBlock(ImmutableList.empty(), ImmutableList.empty(), stmtIf.getElseBranch());
                } else {
                    return new StmtBlock(ImmutableList.empty(), ImmutableList.empty(), ImmutableList.empty());
                }
            }
        }
    }
}
