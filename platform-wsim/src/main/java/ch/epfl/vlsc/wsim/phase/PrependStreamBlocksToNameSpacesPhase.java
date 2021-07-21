package ch.epfl.vlsc.wsim.phase;

import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

import java.util.List;

public class PrependStreamBlocksToNameSpacesPhase implements Phase {


    @Override
    public String getDescription() {
        return "prepends all namespace with \"streamblocks\"";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        PrependTransformation transformation = MultiJ.from(PrependTransformation.class).instance();
        return (CompilationTask) transformation.apply(task);
    }

    @Module
    interface PrependTransformation extends IRNode.Transformation {

        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default QID apply(QID node) {

            List<String> nameSpaceParts = node.parts();
            QID prepended = new QID(
                    ImmutableList.<String>builder()
                            .add("streamblocks")
                            .addAll(nameSpaceParts)
                            .build());
            return prepended;
        }
    }
}
