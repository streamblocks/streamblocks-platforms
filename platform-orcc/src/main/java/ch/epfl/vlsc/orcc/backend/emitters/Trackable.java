package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

@Module
public interface Trackable {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    @Binding(BindingKind.LAZY)
    default Stack<Map<String, Type>> pointers() { return new Stack<>(); }

    default void enter() {
        pointers().push(new HashMap<>());
    }

    default void exit() {
        pointers().pop().forEach((ptr, type) -> release(ptr, type));
    }

    default void track(String ptr, Type type) {
        if (!(isTrackable(type))) {
            return;
        }
        if (!(pointers().empty())) {
            pointers().peek().put(ptr, type);
        }
    }

    default void release(String ptr, Type type) {
        backend().free().apply(type, ptr);
    }

    default void release(String ptr, AliasType type) {
        release(ptr, type.getConcreteType());
    }

    default boolean isTrackable(Type type) {
        return false;
    }

    default boolean isTrackable(AlgebraicType type) {
        return true;
    }

    default boolean isTrackable(SetType type) {
        return true;
    }

    default boolean isTrackable(MapType type) {
        return true;
    }

    default boolean isTrackable(StringType type) {
        return true;
    }

    default boolean isTrackable(ListType type) {
        return isTrackable(type.getElementType());
    }

    default boolean isTrackable(TupleType type) {
        return true;
    }

    default boolean isTrackable(AliasType type) {
        return isTrackable(type.getConcreteType());
    }
}
