package ch.epfl.vlsc.sw.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.*;

@Module
public interface DefaultValues {
    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    String defaultValue(Type type);

    default String defaultValue(CallableType t) {
        return "{ NULL, NULL }";
    }

    default String defaultValue(BoolType t) {
        return "false";
    }

    default String defaultValue(RealType t) {
        return "0.0";
    }

    default String defaultValue(IntType t) {
        return "0";
    }

    default String defaultValue(ListType t) {
        if (t.getSize().isPresent()) {
            StringBuilder builder = new StringBuilder();
            String element = defaultValue(backend().typeseval().innerType(t));

            builder.append("{");
            builder.append(element);
            builder.append("}");
            return builder.toString();
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
