package ch.epfl.vlsc.node.backend;

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
public interface NodeBackend {
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


    // -- Multicore Backend
    @Binding(LAZY)
    default MulticoreBackend multicore() {
        return MultiJ.from(MulticoreBackend.class)
                .bind("task").to(task())
                .bind("context").to(context())
                .instance();
    }

    // -- Vivado HLS Backend
    @Binding(LAZY)
    default VivadoHLSBackend hls() {
        return MultiJ.from(VivadoHLSBackend.class)
                .bind("task").to(task())
                .bind("context").to(context())
                .instance();
    }

    // -- CMakeLists
    @Binding(LAZY)
    default CMakeLists cmakelists(){return MultiJ.from(CMakeLists.class).bind("backend").to(this).instance();}


}
