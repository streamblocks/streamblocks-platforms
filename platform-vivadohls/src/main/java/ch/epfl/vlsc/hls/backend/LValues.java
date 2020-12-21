package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.expr.ExprIndexer;
import se.lth.cs.tycho.ir.expr.ExprPortIndexer;
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

    default String lvalue(LValueField field) {
        return String.format("%s.%s", lvalue(field.getStructure()), field.getField().getName());
    }

    default String lvalue(LValueIndexer indexer) {
        Variable var = evalLValueIndexerVar(indexer);
        return String.format("%s[%s]", variables().name(var), singleDimIndex(indexer));
    }

    default String evaluate(ExprPortIndexer inputIndexer) {
        PortDecl decl = backend().ports().declaration(inputIndexer.getPort());
        if (backend().ports().isInputPort(decl)) {
            if (!backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), inputIndexer.getPort().getName())) {
                Type type = backend().types().portType(inputIndexer.getPort());
                String tmp = variables().generateTemp();
                emitter().emit("pinRead(%s, %s);", inputIndexer.getPort().getName(), tmp);
                return tmp;
            }
        }
        return "0";
    }


    default String singleDimIndex(LValueIndexer indexer) {

        Optional<String> str = Optional.empty();
        String ind = "";

        Variable var = evalLValueIndexerVar(indexer);
        VarDecl varDecl = backend().varDecls().declaration(var);
        Type t = backend().types().declaredType(varDecl);
        ListType listType = null;
        if (t instanceof ListType) {
            listType = (ListType) t;
        } else if (t instanceof RefType) {
            listType = (ListType) ((RefType) t).getType();
        }

        List<Integer> listSizeDim = backend().typeseval().sizeByDimension(listType);

        List<Integer> elementSizeDim = new ArrayList<>();

        if(listType.getElementType() instanceof ListType) {
            elementSizeDim = backend().typeseval().sizeByDimension((ListType) listType.getElementType());
        }

        List<String> indexByDim = new ArrayList<>();
        if (indexer.getStructure() instanceof LValueIndexer) {

            indexByDim = getListIndexes((LValueIndexer) indexer.getStructure());
            elementSizeDim = backend().typeseval().sizeByDimension((ListType) listType.getElementType());
            Collections.reverse(indexByDim);


            List<String> structureIndex = new ArrayList<>();
            for (int i = 0; i < indexByDim.size(); i++) {
                List<String> dims = new ArrayList<>();
                for (int j = i; j < elementSizeDim.size(); j++) {
                    dims.add(Integer.toString(elementSizeDim.get(j)));
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

        //if(!indexByDim.isEmpty()) {
        if (listSizeDim.size() != (indexByDim.size() + 1)) {
            int factor = 1;
            for(int i = indexByDim.size(); i < elementSizeDim.size(); i++){
                factor*=elementSizeDim.get(i);
            }

            //int lastDim = elementSizeDim.get(elementSizeDim.size() - 1);
            ind = String.format("%s*%s", factor, ind);
        }
        //}

        if (str.isPresent()) {
            return String.format("%s + %s",  str.get(), ind);
        } else {
            return String.format("%s", ind);
        }
    }

    default boolean subIndexAccess(LValueIndexer indexer){
        Variable var = evalLValueIndexerVar(indexer);
        VarDecl varDecl = backend().varDecls().declaration(var);
        Type t = backend().types().declaredType(varDecl);
        ListType listType = null;
        if (t instanceof ListType) {
            listType = (ListType) t;
        } else if (t instanceof RefType) {
            listType = (ListType) ((RefType) t).getType();
        }

        List<Integer> listSizeDim = backend().typeseval().sizeByDimension(listType);

        List<String> indexByDim = new ArrayList<>();
        if (indexer.getStructure() instanceof LValueIndexer) {
            indexByDim = getListIndexes((LValueIndexer) indexer.getStructure());
        }
        return listSizeDim.size() != (indexByDim.size() + 1);
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
