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

        if (task() instanceof CompilationTask) {
            PartitionedCompilationTask ptask = (PartitionedCompilationTask) task();
            Optional<Network> hwnetwork = ptask.getPartition(PartitionedCompilationTask.PartitionKind.HW);
            if (hwnetwork.isPresent())
                return memories().getExternalMemories(hwnetwork.get());


        }
        return ImmutableList.empty();
    }
}
