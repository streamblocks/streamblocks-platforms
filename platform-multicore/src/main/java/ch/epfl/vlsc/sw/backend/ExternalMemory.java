package ch.epfl.vlsc.sw.backend;


import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.compiler.PartitionedCompilationTask;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Optional;

@Module
public interface ExternalMemory {

    @Binding(BindingKind.INJECTED)
    CompilationTask task();

    @Binding(BindingKind.INJECTED)
    Memories memories();

    default ImmutableList<Memories.InstanceVarDeclPair> getExternalMemories() {

        ImmutableList.Builder<Memories.InstanceVarDeclPair> memoryResidentVars = ImmutableList.builder();

        if (task() instanceof PartitionedCompilationTask &&
                ((PartitionedCompilationTask) task()).getPartition(PartitionedCompilationTask.PartitionKind.HW)
                        .isPresent()) {
            PartitionedCompilationTask ptask = (PartitionedCompilationTask) task();
            CompilationTask otherTask =
                    task().withNetwork(ptask.getPartition(PartitionedCompilationTask.PartitionKind.HW).get());
            memoryResidentVars.addAll(otherTask.getModule(Memories.key).getExternalMemories(otherTask.getNetwork()));
        }

        return memoryResidentVars.build();
    }
}
