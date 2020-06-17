package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.expr.ExprIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.*;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Module
public interface LValues {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

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
        VarDecl decl = backend().varDecls().declaration(var);
        IRNode parent = backend().tree().parent(decl);
        if((parent instanceof Scope) || (parent instanceof ActorMachine) || (parent instanceof NamespaceDecl)){
            Type type = backend().types().type(decl.getType());
            if(type instanceof ListType){
                backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_STORE += 1;");
            }else{
                backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_STORE += 1;");
            }
        }
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

        if (str.isPresent()) {
            return String.format("%s[%s + %s]", variables().name(var), str.get(), ind);
        } else {
            return String.format("%s[%s]", variables().name(var), ind);
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
