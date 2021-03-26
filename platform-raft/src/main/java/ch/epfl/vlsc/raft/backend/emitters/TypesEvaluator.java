package ch.epfl.vlsc.raft.backend.emitters;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.RealType;
import se.lth.cs.tycho.type.RefType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.Type;
import se.lth.cs.tycho.type.UnitType;

@Module
public interface TypesEvaluator {

    @Binding(BindingKind.INJECTED)
    Types types();

    @Binding(BindingKind.INJECTED)
    Alias alias();

    String type(Type type);


    default String type(IntType type) {
        if (type.getSize().isPresent()) {
            int originalSize = type.getSize().getAsInt();
            int targetSize = 8;
            while (originalSize > targetSize) {
                targetSize = targetSize * 2;
            }
            return String.format(type.isSigned() ? "int%d_t" : "uint%d_t", targetSize);
        } else {
            return type.isSigned() ? "int32_t" : "uint32_t";
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

    default String type(StringType type) {
        return "std::string";
    }

    default String type(BoolType type) {
        return "bool";
    }

    default String type(CallableType type) {
        return type(type.getReturnType());
    }

    default String type(RefType type) {
        return type(type.getType()) + "*";
    }

    default String type(AlgebraicType type) {
        return type.getName();
    }

    /*
    default String type(ListType type) {
        return String.format("std::vector< %s >", type(type.getElementType()));
    }*/


    default String type(ListType type) {
        return String.format("%s", type(type.getElementType()));
    }


    default boolean isAlgebraicTypeList(Type type) {
        if (!(type instanceof ListType)) {
            return false;
        }
        ListType listType = (ListType) type;
        if (listType.getElementType() instanceof AlgebraicType || alias().isAlgebraicType(listType.getElementType())) {
            return true;
        } else {
            return isAlgebraicTypeList(listType.getElementType());
        }
    }

}
