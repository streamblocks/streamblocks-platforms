package ch.epfl.vlsc.compiler;

import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.ToolAttribute;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.compiler.CompilationTask;

import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.network.Network;

import se.lth.cs.tycho.ir.util.Lists;
import se.lth.cs.tycho.reporting.Diagnostic;


import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Mahyar Emami (mahyar.emami@epfl.ch)
 * An extended CompliationTask that can be used in the network partitioning phases.
 * This class is intended for intermediate use only.
 */
public class PartitionedCompilationTask extends CompilationTask {


    public static final String partitionKey = "partition";

    public enum PartitionKind {
        HW, SW, ANY, INVALID;

        @Override
        public String toString() {
            String ret;
            switch (this) {
                case HW:
                    return "hw";
                case SW:
                    return "sw";
                case ANY:
                    return "any";
                case INVALID:
                    return "<INVALID>";
                default:
                    return "<ERROR?";
            }
        }
    };

    public static PartitionKind getPartitionKind(Instance instance) {
        ImmutableList<ToolAttribute> pattrs =
                ImmutableList.from(instance.getAttributes()
                        .stream().filter(a -> a.getName().equals(partitionKey)).collect(Collectors.toList()));

        if (pattrs.size() == 0) {
            return PartitionKind.ANY;
        } else if (pattrs.size() == 1) {
            if (pattrs.get(0) instanceof ToolValueAttribute) {
                ToolValueAttribute attr = (ToolValueAttribute) pattrs.get(0);
                String p = ((ExprLiteral) attr.getValue()).asString().get();
                switch (p) {
                    case "hw":
                        return PartitionKind.HW;
                    case "sw":
                        return PartitionKind.SW;
                    default:
                        return PartitionKind.INVALID;
                }
            } else {
                return PartitionKind.INVALID;
            }
        } else {
            return PartitionKind.INVALID;
        }
    }
    private final Map<PartitionKind, Network> networkPartitions;

    /**
     * Constructs a PartitionedCompilationTask from the CompilationTask
     * @param task
     * @return
     */
    public static PartitionedCompilationTask of(CompilationTask task) {
        return PartitionedCompilationTask.of(task, new HashMap<>());
    }

    /**
     * Constructs a PartitionedCompilationTask from the CompilationTask and partitions
     * @param task
     * @param partitions
     * @return
     */
    public static PartitionedCompilationTask of(CompilationTask task, Map<PartitionKind, Network> partitions) {
        QID identifier = task.getIdentifier();
        Network network = task.getNetwork().deepClone();
        return new PartitionedCompilationTask(task.getSourceUnits(),
                identifier, network, partitions);
    }
    public PartitionedCompilationTask(List<SourceUnit> sourceUnits, QID identifier, Network network) {
        super(sourceUnits, identifier, network);
        this.networkPartitions = new HashMap<>();
    }
    public PartitionedCompilationTask(List<SourceUnit> sourceUnits, QID identifier, Network network,
                                       Map<PartitionKind, Network> networkPartitions) {
        super(sourceUnits, identifier, network);
        this.networkPartitions = networkPartitions;

    }


    public PartitionedCompilationTask copy(List<SourceUnit> sourceUnits, QID identifier, Network network,
                                           Map<PartitionKind, Network> networkPartitions) {
        if (Lists.sameElements(this.getSourceUnits(), sourceUnits)
                && Objects.equals(this.getIdentifier(), identifier)
                && this.getNetwork() == network
                && this.networkPartitions.equals(networkPartitions)) {
            return this;
        } else {
            return new PartitionedCompilationTask(sourceUnits, identifier, network, networkPartitions);
        }
    }

    /**
     * A helper constructor that returns PartitionedCompilationTask with the given SourceUnits
     * @param sourceUnits
     * @return
     */
    @Override
    public PartitionedCompilationTask withSourceUnits(List<SourceUnit> sourceUnits) {
        return copy(sourceUnits, this.getIdentifier(), this.getNetwork(), this.networkPartitions);
    }


    /**
     * A helper constructor that returns a PartitionedCompilationTask with the given network
     * @param network
     * @return
     */
    @Override
    public PartitionedCompilationTask withNetwork(Network network) {
        return copy(this.getSourceUnits(), this.getIdentifier(), network, this.networkPartitions);
    }

    /**
     * A helper constructor that returns a PartitionedCompilationTask wit the given identifier
     * @param identifier
     * @return
     */
    @Override
    public PartitionedCompilationTask withIdentifier(QID identifier) {
        return copy(this.getSourceUnits(), identifier, this.getNetwork(), this.networkPartitions);
    }

    /**
     * A helper constructor that returns a PartitionedCompilationTask with the given networkPartitions
     * @param networkPartitions
     * @return
     */
    public PartitionedCompilationTask withPartitions(Map<PartitionKind, Network> networkPartitions) {
        return copy(this.getSourceUnits(), this.getIdentifier(), this.getNetwork(), networkPartitions);
    }


    /**
     * A helper constructor that returns a PartitionedCompilationTask with the given networkPartitions
     * @param task
     * @return
     */
    public PartitionedCompilationTask withCompilationTask(CompilationTask task) {
        return copy(task.getSourceUnits(), task.getIdentifier(), task.getNetwork(), this.networkPartitions);
    }



    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        this.getSourceUnits().forEach(action);
        if (this.getNetwork() != null) action.accept(this.getNetwork());
        if (this.networkPartitions.isEmpty() == false) {
            for (PartitionKind p: this.networkPartitions.keySet())
                action.accept(this.networkPartitions.get(p));
        }
    }


    @Override
    public PartitionedCompilationTask transformChildren(Transformation transformation) {
        List<SourceUnit> sourceUnits = transformation.mapChecked(SourceUnit.class, this.getSourceUnits());
        Network network = this.getNetwork() == null ? null : transformation
                                                                .applyChecked(Network.class, this.getNetwork());
        Map<PartitionKind, Network> newPartitions =
                this.networkPartitions.isEmpty() ? new HashMap<>() : new HashMap<>(this.networkPartitions);
        if (!this.networkPartitions.isEmpty())
            newPartitions.replaceAll((k, v) -> transformation.applyChecked(Network.class, v));
        return copy(sourceUnits, getIdentifier(), network, newPartitions);
    }

    /**
     * Returns the network partition based on the given partition kind
     * @param partition the kind of partition to extract
     * @return
     */
    public Optional<Network> getPartition(PartitionKind partition) {

        if (this.networkPartitions == null)
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Network is not partitioned!" +
                            "Make sure NetworkPartitioningPhase is enabled."));
        if (this.networkPartitions.containsKey(partition))
            return Optional.of(this.networkPartitions.get(partition));
        else
            return Optional.empty();
    }

    /**
     * returns a vanilla CompilationTask that has the desired network partition
     * @param context - context of compilation for reporting
     * @param kind - the network partition kind of the task network
     * @return - A CompilationTask with the desired network partition in the network field
     */
    public CompilationTask extractPartition(Context context, PartitionKind kind) {
        Optional<Network> network = this.getPartition(kind);
        if (!network.isPresent()) {
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, kind.toString() + " network partition is empty!"));
            return this.withNetwork(null);
        } else {

            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, kind.toString() + " network partition contains " +
                            String.join(", ",
                                    network.get().getInstances().map(Instance::getInstanceName))));
            return this.withNetwork(network.get().deepClone());

        }
    }

}
