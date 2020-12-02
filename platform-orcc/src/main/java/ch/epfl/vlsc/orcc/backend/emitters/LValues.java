package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.ExprIndexer;
import se.lth.cs.tycho.ir.expr.ExprInput;
import se.lth.cs.tycho.ir.stmt.lvalue.*;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.RefType;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Module
public interface LValues {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default Variables variables() {
        return backend().variables();
    }

    default ExpressionEvaluator expressioneval() {
        return backend().expressionEval();
    }

    String lvalue(LValue lvalue);

    default String lvalue(LValueVariable var) {
        return variables().name(var.getVariable());
    }

    default String lvalue(LValueDeref deref) {
        LValueVariable lValueVariable = deref.getVariable();
        Variable var = lValueVariable.getVariable();
        VarDecl decl = backend().varDecls().declaration(var);
        Type type = backend().types().declaredType(decl);
        if (type instanceof ListType) {
            return lvalue(deref.getVariable());
        } else if (type instanceof RefType) {
            return lvalue(deref.getVariable());
        }
        return "(*" + lvalue(deref.getVariable()) + ")";
    }

    default String lvalue(LValueField field) {
        return String.format("%s->members.%s", lvalue(field.getStructure()), field.getField().getName());
    }


    default String lvalue(LValueIndexer indexer) {
        return lvalueIndexing(backend().types().type(indexer.getStructure()), indexer);
    }

    default String lvalueIndexing(Type type, LValueIndexer indexer) {
        return String.format("%s[%s]", lvalue(indexer.getStructure()), backend().expressionEval().evaluate(indexer.getIndex()));
    }


    default String lvalue(LValuePortIndexer indexer) {
        boolean aligned = !backend().alignedBox().isEmpty() && backend().alignedBox().get();
        if (aligned) {
            return String.format("tokens_%1$s[(index_%1$s  %% SIZE_%1$s) + %2$s]", indexer.getPort().getName(), expressioneval().evaluate(indexer.getIndex()));
        } else {
            return String.format("tokens_%1$s[(index_%1$s + (%2$s)) %% SIZE_%1$s]", indexer.getPort().getName(), expressioneval().evaluate(indexer.getIndex()));
        }
    }




    default List<String> getListIndexes(LValueIndexer expr) {
        List<String> indexByDim = new ArrayList<>();
        if (expr.getStructure() instanceof LValueIndexer) {
            indexByDim.add(expressioneval().evaluate(expr.getIndex()));
            getListIndexes((LValueIndexer) expr.getStructure()).stream().forEachOrdered(indexByDim::add);
        } else {
            indexByDim.add(expressioneval().evaluate(expr.getIndex()));
        }

        return indexByDim;
    }

}
