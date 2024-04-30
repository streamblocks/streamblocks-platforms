package ch.epfl.vlsc.mlir.backend;

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
    MlirBackend backend();

    default Types types() {
        return backend().types();
    }

    String type(Type type);

    default String type(AlgebraicType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
        //return type.getName() + "_t*";
    }

    default String type(IntType type) {
        if (type.getSize().isPresent()) {
            int originalSize = type.getSize().getAsInt();
            /*int targetSize = 8;
            while (originalSize > targetSize) {
                targetSize = targetSize * 2;
            }*/
            /*if(targetSize > 64){
                targetSize = 64;
            }*/
            return String.format(type.isSigned() ? "i%d" : "u%d", originalSize);
        } else {
            return type.isSigned() ? "i32" : "u32";
        }
    }

    default String type(RealType type) {
        switch (type.getSize()) {
            case 32:
                return "f32";
            case 64:
                return "f64";
            default:
                throw new UnsupportedOperationException("Unknown real type.");
        }
    }

    default String type(UnitType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
        //return "void";
    }

    default String type(ListType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
        //Type innerType = innerType(type.getElementType());

        //return type(innerType);
    }

    default String pointerType(Type type) {
        return type(type);
    }

    default String pointerType(ListType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
        /*String dims = getPointerDims(type.getElementType());
        if(dims.equals("")){
            return type(type);
        }
        return String.format("%s %s", type(type), dims );*/
    }

    default String type(StringType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
        //return "string_t";
    }

    default String type(CharType type) {
        return "u8";
    }


    default String type(BoolType type) {
        return "i1";
    }

    default String type(CallableType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
        //return type(type.getReturnType());
    }

    default String type(RefType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
        //return type(type.getType()) + "*";
    }


    /**
     * Get The most inner type of a type
     *
     * @param type
     * @return
     */
    default Type innerType(Type type) {
        Type inner;
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


    default String getPointerDims(Type type) {
        return "";
    }

    default String getPointerDims(ListType type) {
        if (type.getElementType() instanceof ListType) {
            return String.format("*%s", getPointerDims((ListType) type.getElementType()));
        } else {
            return "*";
        }
    }

}
