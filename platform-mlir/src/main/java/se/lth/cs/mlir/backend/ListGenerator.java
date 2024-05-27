package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

/**
 * A module that generates MLIR operations for dealing with CAL lists represented as memrefs in MLIR.
 */
@Module
public interface ListGenerator {
    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void allocateList(ListType lvalueType, String lvalueSSA){
        String typeString = backend().typeseval().type(lvalueType);
        backend().emitter().emit("%%%s = memref.alloca() : %s", lvalueSSA, typeString);
    }

    default void store(String listSSA, String ssaToStore, String indexSSA, Type listType){
        emitter().emit("memref.store %%%s, %%%s[%%%s] : %s", ssaToStore, listSSA, indexSSA, backend().typeseval().type(listType));
    }
}