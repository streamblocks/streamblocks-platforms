package ch.epfl.vlsc.unified.ir.network;

import ch.epfl.vlsc.unified.ir.PartitionKind;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.TypeParameter;
import se.lth.cs.tycho.ir.ValueParameter;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;

import java.util.List;
import java.util.Objects;

public class PartitionLink extends Instance{
    public enum LinkKind {TX, RX};
    private final PartitionKind thisPartition;
    private final PartitionKind thatPartition;
    private final LinkKind kind;
    public PartitionLink(String instanceName, QID entityName, List<ValueParameter> valueParameters,
                       List<TypeParameter> typeParameters, PartitionKind thisPartition,
                         PartitionKind thatPartition, LinkKind kind) {
        super(instanceName, entityName, valueParameters, typeParameters);
        this.thisPartition = thisPartition;
        this.thatPartition = thatPartition;
        this.kind = kind;
    }

    public PartitionLink copy(String instanceName, QID entityName, List<ValueParameter> valueParameters,
                            List<TypeParameter> typeParameters, PartitionKind thisPartition,
                              PartitionKind thatPartition, LinkKind kind) {
        if (Objects.equals(this.getInstanceName(), instanceName)
            && Objects.equals(this.getEntityName(), entityName)
            && Lists.sameElements(this.getValueParameters(), valueParameters)
            && Lists.sameElements(this.getTypeParameters(), typeParameters)
            && this.thisPartition == thisPartition
            && this.thatPartition == thatPartition
            && this.kind == kind) {
            return this;
        } else
            return new PartitionLink(instanceName, entityName, valueParameters, typeParameters, thisPartition,
                    thatPartition, kind);
    }

}
