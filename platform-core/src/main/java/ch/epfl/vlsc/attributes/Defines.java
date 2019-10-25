package ch.epfl.vlsc.attributes;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.VariableDeclarations;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Module
public interface Defines extends Consumer<IRNode> {

    @Binding(BindingKind.LAZY)
    default List<VarDecl> variables() {
        return new ArrayList<>();
    }

    @Binding(BindingKind.INJECTED)
    List stateVariables();

    @Binding(BindingKind.INJECTED)
    VariableDeclarations vardecls();

    default List<VarDecl> defines(Transition transition) {
        variables().clear();
        transition.getBody().forEach(this);
        return variables();
    }

    @Override
    default void accept(IRNode node) {
        node.forEachChild(this);
    }

    default void accept(LValueVariable lValueVariable) {
        VarDecl varDecl = vardecls().declaration(lValueVariable);
        if (stateVariables().contains(varDecl)) {
            variables().add(varDecl);
        }
    }

}
