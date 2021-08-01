package ch.epfl.vlsc.wsim.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;
import ch.epfl.vlsc.wsim.backend.WSimBackend;

import java.nio.file.Path;

@Module
public interface CMakeLists {

    @Binding(BindingKind.INJECTED)
    WSimBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }


    default void generatedSubDirectory(Path genPath) {

        emitter().open(genPath.resolve("CMakeLists.txt"));

        emitter().emit("set(ACTOR_SOURCES");
        {
            emitter().increaseIndentation();
            for(Instance instance : backend().task().getNetwork().getInstances()){
                GlobalEntityDecl decl = backend().globalnames().entityDecl(instance.getEntityName(), true);
                if (!decl.getExternal())
                    emitter().emit("${CMAKE_CURRENT_SOURCE_DIR}/src/%s.cpp", instance.getInstanceName());
            }
            emitter().emit("${CMAKE_CURRENT_SOURCE_DIR}/src/%s.cpp", backend().task().getIdentifier().getLast().toString());
            emitter().emit("PARENT_SCOPE");
            emitter().decreaseIndentation();
        }
        emitter().emit(")");

        emitter().emit("set(ACTOR_INCLUDES");
        {
            emitter().increaseIndentation();
            emitter().emit("${CMAKE_CURRENT_SOURCE_DIR}/include");
            emitter().emit("PARENT_SCOPE");
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().close();
    }


}
