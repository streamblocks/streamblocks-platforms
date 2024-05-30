package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.StackSSA;
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

    default StackSSA ssaValueNumberingStack() {
        return backend().ssaValueNumberingStack();
    }

    default Emitter emitter() {
        return backend().emitter();
    }

    String type(Type type);

    default String type(AlgebraicType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
    }

    String mlirTypeConstantInstruction(Type type, String ssaOperandIn);

    default String mlirTypeConstantInstruction(IntType type, String ssaOperandIn) {
        return "arith.constant " + ssaOperandIn + " : " + type(type);
    }

    default String type(IntType type) {
        if (type.getSize().isPresent()) {
            int originalSize = type.getSize().getAsInt();
            return String.format(type.isSigned() ? "i%d" : "i%d", originalSize);
        } else {
            return type.isSigned() ? "i32" : "i32"; // i32 represents both signed and unsigned in MLIR
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
        Type innerType = type.getElementType();
        int sizeInt = type.getSize().orElse(0);
        String size = "" + sizeInt;
        // Lists can contains lists (lists of lists of lists ...). In this case we need to get the dimensions all the
        // way down
        while (innerType instanceof ListType) {
            ListType innerTypeAsList = (ListType) innerType;
            sizeInt = innerTypeAsList.getSize().orElse(0);
            size = size + "x" + sizeInt;
            innerType = innerTypeAsList.getElementType();
        }
        return "memref<" + size + "x" + type(innerType) + ">";
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
        return "i8";
    }


    default String type(BoolType type) {
        return "i1";
    }

    default String type(CallableType type) {
        //throw new UnsupportedOperationException("Type not implemented in MLIR.");
        return type(type.getReturnType());
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

    /**
     * Generate the MLIR to cast an operand from one type to another
     *
     * @param fromType The current type of the operand
     * @param toType   The type to cast the operand to
     * @param inputSSA String representing the SSA operand name of the operand being converted
     * @return String of the SSA operand representing the converted value
     * @throws Error If no conversion has been implemented or is possible
     */
    default String castType(Type fromType, Type toType, String inputSSA) {
        throw new Error("Type conversion not implemented from " + fromType + " to " + toType);
    }

    default String castType(BoolType fromType, BoolType toType, String inputSSA) {
        return inputSSA;
    }

    default String castType(IntType fromType, IntType toType, String inputSSA) {
        if (fromType.getSize().orElse(32) == toType.getSize().orElse(32)) {
            // 1. If the from and to type is the same, do nothing
            return inputSSA;
        } else {
            String outSSA = ssaValueNumberingStack().getNewTempVar();
            // 2. Sign extend if the destination is greater than the source
            if (toType.getSize().orElse(32) >= fromType.getSize().orElse(32)) {
                // 2.1 Commands are different based on the sign
                if (fromType.isSigned()) {
                    emitter().emit("%%%s = arith.extsi %%%s : %s to %s", outSSA, inputSSA, type(fromType),
                            type(toType));
                } else {
                    emitter().emit("%%%s = arith.extui %%%s : %s to %s", outSSA, inputSSA, type(fromType),
                            type(toType));
                }
            } else {
                // 3. Truncate if the destination is greater than the source
                emitter().emit("%%%s = arith.trunci %%%s : %s to %s", outSSA, inputSSA, type(fromType), type(toType));
            }
            return outSSA;
        }
    }

    default String castType(ListType fromType, ListType toType, String listNameSSA) {
        List<Integer> fromListDimension = sizeByDimension(fromType);
        List<Integer> toListDimension = sizeByDimension(fromType);

        if (!fromListDimension.equals(toListDimension)) {
            throw new UnsupportedOperationException("Casting from one list type to another when the lists are not of " +
                    "equal size is not supported.");
        }

        if (fromType.equals(toType)) {
            return listNameSSA;
        }

        List<Integer> sizeByDim = sizeByDimension(toType);
        List<String> indices = new ArrayList<>();
        String convertedListSSA = ssaValueNumberingStack().getNewTempVar();
        backend().lists().allocateList(toType, convertedListSSA);

        // In the case of a list of lists, we need to cast through ever layer of the list.
        castSubList(convertedListSSA, listNameSSA, indices, sizeByDim, fromType, toType, fromType, toType);

        return convertedListSSA;
    }

    default void castSubList(String convertedListSSA, String inputListSSA, List<String> indices,
                             List<Integer> sizeByDim, ListType fromType,
                             ListType toType, ListType topFromType, ListType topToType) {
        int range = sizeByDim.remove(0);
        Type innerTypeFrom = fromType.getElementType();
        Type innerTypeTo = toType.getElementType();
        for (int i = 0; i < range; i++) {
            String indexSSA = backend().lists().generateIndexFromInt(i);
            indices.add(indexSSA);
            castSubList(convertedListSSA, inputListSSA, indices, sizeByDim, innerTypeFrom, innerTypeTo, topFromType,
                    topToType);
            indices.remove(indices.size() - 1);
        }
        sizeByDim.add(0, range);
    }

    default void castSubList(String convertedListSSA, String inputListSSA, List<String> indices,
                             List<Integer> sizeByDim, Type fromType,
                             Type toType, ListType topFromType, ListType topToType) {
        String tempSSAFromList = ssaValueNumberingStack().getNewTempVar();
        backend().lists().load(inputListSSA, tempSSAFromList, indices, topFromType);
        String convertedSSA = castType(fromType, toType, tempSSAFromList);
        backend().lists().store(convertedListSSA, convertedSSA, indices, topToType);
    }

}
