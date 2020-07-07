package ch.epfl.vlsc.sw.ir;


import se.lth.cs.tycho.ir.decl.ParameterTypeDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;

import java.util.List;

public class PartitionLink extends Entity {

    /**
     * PartitionHandle, represents an OpenCL communication class, specific to Multicore backend
     */
    private final PartitionHandle handle;


    public PartitionLink(List<PortDecl> inputPorts, List<PortDecl> outputPorts, PartitionHandle handle) {
        super(null, ImmutableList.empty(), inputPorts, outputPorts, null, null);
        this.handle = handle;
    }

    @Override
    public PartitionLink transformChildren(Transformation transformation) {
        return new PartitionLink(
                (ImmutableList) inputPorts.map(transformation),
                (ImmutableList) outputPorts.map(transformation), this.handle);

    }


    public PartitionHandle getHandle() {
        return this.handle;
    }

    @Override
    public PartitionLink withTypeParameters(List<ParameterTypeDecl> typeParameters) {
        return new PartitionLink(inputPorts, outputPorts, handle);
    }

    @Override
    public PartitionLink withValueParameters(List<ParameterVarDecl> valueParameters) {
        return new PartitionLink(inputPorts, outputPorts, handle);
    }
}

