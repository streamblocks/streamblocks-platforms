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
import java.util.OptionalInt;

@Module
public interface TypesEvaluator {

    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default Types types() {
        return backend().types();
    }

    default StackSSA ssaValueNumberingStack() { return backend().ssaValueNumberingStack(); }

    default Emitter emitter() {
        return backend().emitter();
    }

    String type(Type type);

    default String type(AlgebraicType type) {
        throw new UnsupportedOperationException("Type not implemented in MLIR.");
        //return type.getName() + "_t*";
    }

    String mlirTypeConstantInstruction(Type type);

    default String mlirTypeConstantInstruction(IntType type) {
        return "arith.constant";
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
        return "i8";
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

    /**
     * Return a common type that two different types can be cast to.
     * @param lhs First type to compare
     * @param rhs Second type to compare
     * @return Common type between these two types
     * @throws Error If there is no common type.
     */
    default Type getCommonType(Type lhs, Type rhs){
        throw new Error("getCommonType() not implemented for lhs = " + lhs + " and rhs = " + rhs);
    }

    default Type getCommonType(BoolType lhs, BoolType rhs){
        return lhs;
    }

    default Type getCommonType(NumberType lhs, NumberType rhs){
        throw new Error("getCommonType() not implemented for lhs = " + lhs + " and rhs = " + rhs);
    }

    /**
     * Find a common type betweem two integer types. To do this we
     *  1. Compare signs, if the signs are equal, then we can find a common type
     *  2. Compare size (in bits), we take the largest number of bits as the common bit size
     * In the case where one type is signed and the other is unsigned
     *
     * @param lhs The integer type of the lhs argument
     * @param rhs THe integer type of the rhs argument
     * @return A type that both arguments can be converted to.
     */
    default Type getCommonType(IntType lhs, IntType rhs){
        int lsize = lhs.getSize().orElse(32);
        int rsize = rhs.getSize().orElse(32);
        int biggestSize = lsize > rsize ? lsize : rsize;
        if(lhs.isSigned() == rhs.isSigned()){
            return new IntType(OptionalInt.of(biggestSize), lhs.isSigned());
        }else{
            if(
                    lsize >= rsize && !lhs.isSigned() ||
                            rsize >= lsize && !rhs.isSigned()
            ){
                throw new Error("No common type where uint is >= int in size. lhs = " + lhs + " and rhs = " + rhs);
            }else{
                return new IntType(OptionalInt.of(biggestSize), true);
            }
        }
    }

    /**
     * Generate the MLIR to cast an operand from one type to another
     * @param fromType The current type of the operand
     * @param toType The type to cast the operand to
     * @param varName String representing the SSA operand name of the operand being converted
     * @return String of the SSA operand representing the converted value
     * @throws Error If no conversion has been implemented or is possible
     */
    default String castType(Type fromType, Type toType , String varName){
        throw new Error("Type conversion not implemented from " + fromType + " to = " + toType);
    }

    default String castType(BoolType fromType, BoolType toType , String varName){
        return varName;
    }

    /**
     * Cast an integer operand from one type to another
     * @param fromType The type the integer currently is
     * @param toType The type you want to convert it to
     * @param varName String representing the SSA operand name of the integer being converted
     * @return String of the SSA operand representing the converted value
     */
    default String castType(IntType fromType, IntType toType , String varName){
        if(fromType.isSigned() == toType.isSigned() && fromType.getSize().orElse(32) == toType.getSize().orElse(32)){
            // 1. If the from and to type is the same, do nothing
            return varName;
        }else{
            String outVar = ssaValueNumberingStack().getNewTempVar();
            // 2. Extend the integer based on the sign
            if(fromType.isSigned()){
                emitter().emit("%%%s = arith.extsi %%%s : %s to %s", outVar, varName, type(fromType), type(toType));
            }else{
                emitter().emit("%%%s = arith.extui %%%s : %s to %s", outVar, varName, type(fromType), type(toType));
            }
            return outVar;
        }
    }

}
