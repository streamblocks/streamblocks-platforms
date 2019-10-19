package ch.epfl.vlsc.sw.backend;

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

        Optional<String> str = Optional.empty();
        String ind;
        if (indexer.getStructure() instanceof LValueIndexer) {
            VarDecl varDecl = backend().varDecls().declaration(var);
            ListType type = (ListType) backend().types().declaredType(varDecl);
            str = Optional.of(evalLValueIndexStructure((LValueIndexer) indexer.getStructure(), (ListType) type.getElementType()));
        }

        if (indexer.getIndex() instanceof ExprIndexer) {
            ind = String.format("%s", expressioneval().evaluate(indexer.getIndex()));
        } else {
            ind = expressioneval().evaluate(indexer.getIndex());
        }

        if(str.isPresent()){
            return String.format("%s[%s + %s]", variables().name(var), ind, str.get());
        }else{
            return String.format("%s[%s]", variables().name(var), ind);
        }
    }

    default String evalLValueIndexStructure(LValueIndexer indexer, ListType type) {
        String index = expressioneval().evaluate(indexer.getIndex());
        String structure;
        if (indexer.getStructure() instanceof LValueIndexer) {
            ListType lastListType = getLastListType(type);
            structure = String.format("%d*%s", lastListType.getSize().getAsInt(), index);
            structure = String.format("%s + %d*%s", structure, type.getSize().getAsInt(), evalLValueIndexStructure((LValueIndexer) indexer.getStructure(), (ListType) type.getElementType()));
        } else {
            structure = String.format("%d*%s", type.getSize().getAsInt(), index);
        }
        return structure;
    }

    default ListType getLastListType(ListType type){
        if(type.getElementType() instanceof  ListType){
            return getLastListType((ListType) type.getElementType());
        }else{
            return type;
        }
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
