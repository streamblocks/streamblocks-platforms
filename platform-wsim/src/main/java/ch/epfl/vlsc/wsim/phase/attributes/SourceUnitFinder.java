package ch.epfl.vlsc.wsim.phase.attributes;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.MultiJ;
import org.multij.Module;

import se.lth.cs.tycho.attribute.ModuleKey;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.phase.TreeShadow;

public interface SourceUnitFinder {

    ModuleKey<SourceUnitFinder> key = task -> MultiJ.from(Implementation.class)
            .bind("tree").to(task.getModule(TreeShadow.key))
            .instance();

    SourceUnit find(IRNode node);

    @Module
    interface Implementation extends SourceUnitFinder{
        @Binding(BindingKind.INJECTED)
        TreeShadow tree();

        default SourceUnit find(IRNode node) {
            return find(tree().parent(node));
        }
        default SourceUnit find(SourceUnit src) {
            return src;
        }

    }
}
