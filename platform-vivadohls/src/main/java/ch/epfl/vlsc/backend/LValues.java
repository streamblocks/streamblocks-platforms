package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.ExprIndexer;
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
    VivadoHLSBackend backend();

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
        return String.format("%s[%s]", variables().name(var), evalLValueIndexer(indexer, 0));
    }


    Variable evalLValueIndexerVar(LValue lvalue);

    default Variable evalLValueIndexerVar(LValueVariable var) {
        return var.getVariable();
    }

    default Variable evalLValueIndexerVar(LValueIndexer indexer) {
        return evalLValueIndexerVar(indexer.getStructure());
    }

    default Variable evalLValueIndexerVar(LValueDeref deref) {
        return evalLValueIndexerVar(deref.getVariable());
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
            index++;
            int factor = sizeByDimension.get(index);
            String i;
            if (indexer.getIndex() instanceof ExprIndexer) {
                ExprIndexer ii = (ExprIndexer) indexer.getIndex();
                i = String.format("%s[%s]", expressioneval().evalExprIndex(ii.getStructure(), index), expressioneval().evalExprIndex(ii.getIndex(), index));
            } else {
                i = expressioneval().evalExprIndex(indexer.getIndex(), index);
            }
            String s = evalLValueIndexer(indexer.getStructure(), index);
            return String.format("(%s + (%s * %d))", i, s, factor);
        } else {
            if (indexer.getIndex() instanceof ExprIndexer) {
                ExprIndexer ii = (ExprIndexer) indexer.getIndex();
                return String.format("%s[%s]", expressioneval().evalExprIndex(ii.getStructure(), index), expressioneval().evalExprIndex(ii.getIndex(), index));
            } else {
                return expressioneval().evalExprIndex(indexer.getIndex(), index);
            }
        }
    }


}
