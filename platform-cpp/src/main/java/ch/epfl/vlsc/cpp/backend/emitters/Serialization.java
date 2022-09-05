package ch.epfl.vlsc.cpp.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.AliasType;
import se.lth.cs.tycho.type.MapType;
import se.lth.cs.tycho.type.SetType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.TupleType;
import se.lth.cs.tycho.type.Type;

@Module
public interface Serialization {

    @Binding(BindingKind.INJECTED)
    TypesEvaluator typeseval();

    @Binding(BindingKind.INJECTED)
    Emitter emitter();

    @Binding(BindingKind.INJECTED)
    SizeOf sizeof();

    default void write(Type type, String variable, String buffer) {
        emitter().emit("*(%s*) %s = %s;", typeseval().type(type), buffer, variable);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void write(AlgebraicType type, String variable, String buffer) {
        emitter().emit("write_%s(%s, %s);", typeseval().type(type), variable, buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void write(SetType type, String variable, String buffer) {
        emitter().emit("write_%s(%s, %s);", typeseval().type(type), variable, buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void write(MapType type, String variable, String buffer) {
        emitter().emit("write_%s(%s, %s);", typeseval().type(type), variable, buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void write(TupleType type, String variable, String buffer) {
        emitter().emit("write_%s(%s, %s);", typeseval().type(type), variable, buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void write(StringType type, String variable, String buffer) {
        emitter().emit("write_%s(%s, %s);", typeseval().type(type), variable, buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void write(AliasType type, String variable, String buffer) {
        write(type.getConcreteType(), variable, buffer);
    }

    default void read(Type type, String variable, String buffer) {
        emitter().emit("%s = *(%s*) %s;", variable, typeseval().type(type), buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void read(AlgebraicType type, String variable, String buffer) {
        emitter().emit("%s = read_%s(%s);", variable, typeseval().type(type), buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void read(SetType type, String variable, String buffer) {
        emitter().emit("%s = read_%s(%s);", variable, typeseval().type(type), buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void read(MapType type, String variable, String buffer) {
        emitter().emit("%s = read_%s(%s);", variable, typeseval().type(type), buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void read(TupleType type, String variable, String buffer) {
        emitter().emit("%s = read_%s(%s);", variable, typeseval().type(type), buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void read(StringType type, String variable, String buffer) {
        emitter().emit("%s = read_%s(%s);", variable, typeseval().type(type), buffer);
        emitter().emit("%s += %s;", buffer, sizeof().evaluate(type, variable));
    }

    default void read(AliasType type, String variable, String buffer) {
        read(type.getConcreteType(), variable, buffer);
    }
}
