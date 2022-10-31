package ch.epfl.vlsc.sw.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.type.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Module
public interface TypesEvaluator {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    default Types types() {
        return backend().types();
    }

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
            if(targetSize > 64){
                targetSize = 64;
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

    default String type(TensorType type) {
        return "Tensor";
    }

    default String type(ListType type) {

        Type innerType = innerType(type.getElementType());

        return type(innerType);
    }

    default String pointerType(Type type){
        return type(type);
    }

    default String pointerType(ListType type){
        String dims = getPointerDims(type.getElementType());
        if(dims.equals("")){
            return type(type);
        }
        return String.format("%s %s", type(type), dims );
    }

    default String type(StringType type) {
        return "std::string";
    }

    default String type(TupleType type) {
        String tupleTypes = type.getTypes().stream().sequential().map(e -> backend().typeseval().type(e)).collect(Collectors.joining(", "));
        return String.format("std::tuple< %s >", tupleTypes);
    }

    default String type(CharType type) {
        return "char";
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


    default String getPointerDims(Type type){
        return "";
    }

    default String getPointerDims(ListType type){
        if(type.getElementType() instanceof ListType){
            return String.format("*%s", getPointerDims((ListType)type.getElementType()));
        }else{
            return "*";
        }
    }

}
