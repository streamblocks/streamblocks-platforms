package ch.epfl.vlsc.sw.ir;



import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.util.Lists;


import java.util.List;

public class PartitionLink extends Entity {

    /**
     * PartitionHandle, represents an OpenCL communication class, specific to Multicore backend
     */
    private final PartitionHandle handle;

    public PartitionLink(List<PortDecl> inputPorts, List<PortDecl> outputPorts) {
        this(inputPorts, outputPorts, null);
    }

    public PartitionLink(List<PortDecl> inputPorts, List<PortDecl> outputPorts, PartitionHandle handle) {
        super(null, inputPorts, outputPorts, null, null);
        this.handle = handle;
    }

    @Override
    public PartitionLink transformChildren(Transformation transformation) {
        return new PartitionLink(
                (ImmutableList) inputPorts.map(transformation),
                (ImmutableList) outputPorts.map(transformation));

    }


}

