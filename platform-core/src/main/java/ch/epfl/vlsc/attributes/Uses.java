package ch.epfl.vlsc.attributes;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.VariableDeclarations;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.Condition;
import se.lth.cs.tycho.ir.entity.am.PredicateCondition;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.expr.ExprVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


@Module
public interface Uses extends Consumer<IRNode> {

    @Binding(BindingKind.LAZY)
    default List<VarDecl> variables() {
        return new ArrayList<>();
    }

    @Binding(BindingKind.INJECTED)
    List stateVariables();

    @Binding(BindingKind.INJECTED)
    VariableDeclarations vardecls();

    default List<VarDecl> uses(Transition transition) {
        variables().clear();
        transition.getBody().forEach(this);

        return variables();
    }

    default List<VarDecl> uses(Condition condition){
        if(condition.kind() != Condition.ConditionKind.predicate){
            return new ArrayList<>();
        }else{
            PredicateCondition c = (PredicateCondition) condition;
            accept(c.getExpression());
            return variables();
        }
    }


    @Override
    default void accept(IRNode node) {
        node.forEachChild(this);
    }

    default void accept(ExprVariable exprVariable) {
        VarDecl varDecl = vardecls().declaration(exprVariable);
        if (stateVariables().contains(varDecl)) {
            variables().add(varDecl);
        }
    }
}

