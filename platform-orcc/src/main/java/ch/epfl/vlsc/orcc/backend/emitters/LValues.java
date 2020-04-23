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
        return "(*" + lvalue(deref.getVariable()) + ")";
    }

    default String lvalue(LValueField field) {
        return String.format("%s->members.%s", lvalue(field.getStructure()), field.getField().getName());
    }


    default String lvalue(LValueIndexer indexer) {
        Variable var = evalLValueIndexerVar(indexer);

        VarDecl decl = backend().varDecls().declaration(var);
        boolean isInput = false;
        Port port = null;
        if (decl.getValue() != null) {
            if (decl.getValue() instanceof ExprInput) {
                ExprInput e = (ExprInput) decl.getValue();
                if (e.hasRepeat()) {
                    isInput = true;
                    port = e.getPort();
                }
            }
        }

        Optional<String> str = Optional.empty();
        String ind;
        if (indexer.getStructure() instanceof LValueIndexer) {
            VarDecl varDecl = backend().varDecls().declaration(var);
            ListType type = (ListType) backend().types().declaredType(varDecl);

            List<Integer> sizeByDim = backend().typesEval().sizeByDimension((ListType) type.getElementType());
            List<String> indexByDim = getListIndexes((LValueIndexer) indexer.getStructure());
            Collections.reverse(indexByDim);

            List<String> structureIndex = new ArrayList<>();
            for (int i = 0; i < indexByDim.size(); i++) {
                List<String> dims = new ArrayList<>();
                for (int j = i; j < sizeByDim.size(); j++) {
                    dims.add(Integer.toString(sizeByDim.get(j)));
                }
                structureIndex.add(String.format("%s*%s", String.join("*", dims), indexByDim.get(i)));
            }
            str = Optional.of(String.join(" + ", structureIndex));
        }

        if (indexer.getIndex() instanceof ExprIndexer) {
            ind = String.format("%s", expressioneval().evaluate(indexer.getIndex()));
        } else {
            ind = expressioneval().evaluate(indexer.getIndex());
        }

        boolean aligned = backend().alignedBox().isEmpty() ? false : backend().alignedBox().get();
        if (str.isPresent()) {
            if (isInput) {
                if(aligned){
                    return String.format("tokens_%s[(index_%1$s %% SIZE_%1$s) + (%s + %s))]", port.getName(), str.get(), ind);
                }else{
                    return String.format("tokens_%s[(index_%1$s + (%s + %s)) %% SIZE_%1$s]", port.getName(), str.get(), ind);
                }
            } else {
                return String.format("%s[%s + %s]", variables().name(var), str.get(), ind);
            }
        } else {
            if(isInput){
                if(aligned){
                    return String.format("tokens_%s[(index_%1$s %% SIZE_%1$s) + %s]", port.getName(), ind);
                }else{
                    return String.format("tokens_%s[(index_%1$s + (%s)) %% SIZE_%1$s]", port.getName(), ind);
                }
            }else{
                return String.format("%s[%s]", variables().name(var), ind);
            }
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

}
