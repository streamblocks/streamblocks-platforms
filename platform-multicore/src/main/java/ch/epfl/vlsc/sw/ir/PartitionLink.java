package ch.epfl.vlsc.sw.ir;



import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;


import java.util.List;

public class PartitionLink extends Entity {

    public PartitionLink(List<PortDecl> inputPorts, List<PortDecl> outputPorts) {
        super(null, inputPorts, outputPorts, null, null);

    }
    @Override
    public PartitionLink transformChildren(Transformation transformation) {
        return new PartitionLink(
                (ImmutableList) inputPorts.map(transformation),
                (ImmutableList) outputPorts.map(transformation));

    }
}
