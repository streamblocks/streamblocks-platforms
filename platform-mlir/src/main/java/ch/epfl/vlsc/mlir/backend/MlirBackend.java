package ch.epfl.vlsc.mlir.backend;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.sw.backend.MulticoreBackend;
import org.multij.Binding;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;

import static org.multij.BindingKind.INJECTED;
import static org.multij.BindingKind.LAZY;

@Module
public interface MlirBackend {
    // -- Compilation Task
    @Binding(INJECTED)
    CompilationTask task();

    // -- Compilation Context
    @Binding(INJECTED)
    Context context();

    // -- Emitter
    @Binding(LAZY)
    default Emitter emitter() {
        return new Emitter();
    }

}
