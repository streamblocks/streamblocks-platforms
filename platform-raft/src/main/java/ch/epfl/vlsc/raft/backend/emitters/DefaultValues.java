package ch.epfl.vlsc.raft.backend.emitters;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.AliasType;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.CharType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.MapType;
import se.lth.cs.tycho.type.RealType;
import se.lth.cs.tycho.type.SetType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.TupleType;
import se.lth.cs.tycho.type.Type;

@Module
public interface DefaultValues {

    @Binding(BindingKind.INJECTED)
    TypesEvaluator typeseval();

    String defaultValue(Type type);

    default String defaultValue(CallableType t) {
        return "{ NULL, NULL }";
    }

    default String defaultValue(BoolType t) {
        return "false";
    }

    default String defaultValue(CharType t) {
        return "0";
    }

    default String defaultValue(RealType t) {
        return "0.0";
    }

    default String defaultValue(IntType t) {
        return "0";
    }

    default String defaultValue(ListType t) {
        if (t.getSize().isPresent()) {
            String element = defaultValue(t.getElementType());
            return String.format("{%s}", element);
            //return String.format("%s(%s,%s)", typeseval().type(t), t.getSize().getAsInt(), element);
        } else {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    default String defaultValue(AlgebraicType t) {
        return "NULL";
    }

    default String defaultValue(AliasType t) {
        return defaultValue(t.getConcreteType());
    }

    default String defaultValue(TupleType t) {
        return "NULL";
    }

    default String defaultValue(SetType t) {
        return "NULL";
    }

    default String defaultValue(MapType t) {
        return "NULL";
    }

    default String defaultValue(StringType t) {
        return "NULL";
    }
}
