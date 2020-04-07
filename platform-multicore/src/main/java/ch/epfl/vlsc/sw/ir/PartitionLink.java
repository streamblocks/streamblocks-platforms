package ch.epfl.vlsc.sw.ir;


import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.List;

public class PartitionLink extends Entity {

    public PartitionLink(List<PortDecl> inputPorts, List<PortDecl> outputPorts) {
        super(null, inputPorts, outputPorts, null, null);
        throw new CompilationException(
                new Diagnostic(Diagnostic.Kind.ERROR, "PartitionLink entity not implemented"));
    }
    @Override
    public IRNode transformChildren(Transformation transformation) {
        return null;
    }
}
