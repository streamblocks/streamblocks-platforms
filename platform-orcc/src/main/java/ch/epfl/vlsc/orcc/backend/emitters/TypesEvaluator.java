package ch.epfl.vlsc.orcc.backend.emitters;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.type.*;

import java.util.ArrayList;
import java.util.List;

@Module
public interface TypesEvaluator {

    @Binding(BindingKind.INJECTED)
    Types types();

    String type(Type type);

    default String type(AlgebraicType type) {
        return type.getName() + "_t*";
    }

    default String type(IntType type) {
        if (type.getSize().isPresent()) {
            int originalSize = type.getSize().getAsInt();
            int targetSize = 8;
            while (originalSize > targetSize) {
                targetSize = targetSize * 2;
            }
            return String.format(type.isSigned() ? "i%d" : "u%d", targetSize);
        } else {
            return type.isSigned() ? "i32" : "u32";
        }
    }

    default String type(RealType type) {
        switch (type.getSize()) {
            case 32:
                return "float";
            case 64:
                return "double";
            default:
                throw new UnsupportedOperationException("Unknown real type.");
        }
    }

    default String type(UnitType type) {
        return "void";
    }

    default String type(ListType type) {

        Type innerType = innerType(type.getElementType());

        return type(innerType);
    }

    default String type(StringType type) {
        return "string_t";
    }

    default String type(BoolType type) {
        return "bool_t";
    }

    default String type(CharType type) { return "char"; }

    default String type(CallableType type) {
        return type(type.getReturnType());
    }

    default String type(RefType type) {
        return type(type.getType()) + "*";
    }

    String printFormat(Type type);

    default String printFormat(BoolType type) {
        return "i";
    }

    default String printFormat(IntType type) {
        if (type.getSize().isPresent()) {
            if (type.getSize().getAsInt() <= 32) {
                return type.isSigned() ? "i" : "u";
            } else {
                return type.isSigned() ? "lli" : "llu";
            }
        } else {
            return type.isSigned() ? "i" : "u";
        }
    }

    default String printFormat(RealType type) {
        return "f";
    }

    default String printFormat(StringType type) {
        return "s";
    }

    /**
     * Get The most inner type of a type
     *
     * @param type
     * @return
     */
    default Type innerType(Type type) {
        Type inner = null;
        if (type instanceof ListType) {
            inner = innerType((((ListType) type).getElementType()));
        } else {
            inner = type;
        }
        return inner;
    }

    default Integer listDimensions(ListType type) {
        Integer dim = 0;
        if (type.getElementType() instanceof ListType) {
            dim += listDimensions(((ListType) type.getElementType()));
        } else {
            dim = 1;
        }

        return dim;
    }

    default List<Integer> sizeByDimension(ListType type) {
        List<Integer> sizeByDim = new ArrayList<>();
        if (type.getElementType() instanceof ListType) {
            sizeByDim.add(type.getSize().getAsInt());
            sizeByDimension(((ListType) type.getElementType())).stream().forEachOrdered(sizeByDim::add);
        } else {
            sizeByDim.add(type.getSize().getAsInt());
        }

        return sizeByDim;
    }

    default boolean isAlgebraicTypeList(ListType type) {
        if (type.getElementType() instanceof AlgebraicType) {
            return true;
        } else if (!(type.getElementType() instanceof ListType)) {
            return false;
        } else {
            return isAlgebraicTypeList((ListType) type.getElementType());
        }
    }
}
