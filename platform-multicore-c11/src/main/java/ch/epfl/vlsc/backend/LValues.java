package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueDeref;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.List;

@Module
public interface LValues {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default Variables variables() {
        return backend().variables();
    }

    default ExpressionEvaluator expressioneval() {
        return backend().expressioneval();
    }

    String lvalue(LValue lvalue);

    default String lvalue(LValueVariable var) {
        return variables().name(var.getVariable());
    }

    default String lvalue(LValueDeref deref) {
        return "(*" + lvalue(deref.getVariable()) + ")";
    }

    default String lvalue(LValueIndexer indexer) {
        Variable var = evalLValueIndexerVar(indexer);
        return String.format("%s.p[%s]", variables().name(var), evalLValueIndexer(indexer, 0));
    }


    Variable evalLValueIndexerVar(LValue lvalue);

    default Variable evalLValueIndexerVar(LValueVariable var) {
        return var.getVariable();
    }

    default Variable evalLValueIndexerVar(LValueIndexer indexer) {
        return evalLValueIndexerVar(indexer.getStructure());
    }

    default String evalLValueIndexer(LValue lvalue, int index) {
        return lvalue(lvalue);
    }

    default String evalLValueIndexer(LValueIndexer indexer, int index) {
        if (indexer.getStructure() instanceof LValueIndexer) {
            Variable var = evalLValueIndexerVar(indexer);
            VarDecl varDecl = backend().varDecls().declaration(var);
            Type type = backend().types().declaredType(varDecl);
            List<Integer> sizeByDimension = backend().typeseval().sizeByDimension((ListType) type);
            int factor = sizeByDimension.get(index);
            index++;
            return "(" + expressioneval().evalExprIndex(indexer.getIndex(), index) + " + (" + evalLValueIndexer(indexer.getStructure(), index) + " * " + factor + "))";

        } else {
            return expressioneval().evalExprIndex(indexer.getIndex(), index);
        }

    }


}
