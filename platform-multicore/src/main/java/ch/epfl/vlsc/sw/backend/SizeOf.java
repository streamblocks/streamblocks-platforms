package ch.epfl.vlsc.sw.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.*;

@Module
public interface SizeOf {

    @Binding(BindingKind.INJECTED)
    TypesEvaluator typeseval();

    default String evaluate(Type type, String variable) {
        return String.format("sizeof(%s)", typeseval().type(type));
    }

    default String evaluate(AlgebraicType type, String variable) {
        return String.format("size_%s(%s)", typeseval().type(type), variable);
    }

    default String evaluate(SetType type, String variable) {
        return String.format("size_%s(%s)", typeseval().type(type), variable);
    }

    default String evaluate(MapType type, String variable) {
        return String.format("size_%s(%s)", typeseval().type(type), variable);
    }

    default String evaluate(TupleType type, String variable) {
        return String.format("size_%s(%s)", typeseval().type(type), variable);
    }

    default String evaluate(StringType type, String variable) {
        return String.format("size_%s(%s)", typeseval().type(type), variable);
    }

    default String evaluate(AliasType type, String variable) {
        return evaluate(type.getConcreteType(), variable);
    }
}