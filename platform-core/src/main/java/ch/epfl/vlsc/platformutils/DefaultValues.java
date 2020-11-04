package ch.epfl.vlsc.platformutils;

import org.multij.Module;
import se.lth.cs.tycho.type.*;

@Module
public interface DefaultValues {
    String defaultValue(Type type);

    default String defaultValue(CallableType t) {
        return "{ NULL, NULL }";
    }

    default String defaultValue(BoolType t) {
        return "false";
    }

    default String defaultValue(StringType t) {
        return "";
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
            String element = defaultValue(t.getElementType());

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
}
