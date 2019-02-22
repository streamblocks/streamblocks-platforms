package ch.epfl.vlsc.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.type.*;

import java.util.ArrayList;
import java.util.List;

@Module
public interface TypesEvaluator {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Types types() {
        return backend().types();
    }

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

    default String type(ListType type) {

        Type innerType = innerType(type.getElementType());

        return "__array4" + type(innerType);
    }

    default String type(StringType type) {
        return "char *";
    }

    default String type(BoolType type) {
        return "_Bool";
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
        Type inner = null;
        if (type instanceof ListType) {
            inner = innerType((((ListType) type).getElementType()));
        } else {
            inner = type;
        }
        return inner;
    }

    default Integer listDimensions(ListType type){
        Integer dim = 0;
        if(type.getElementType() instanceof ListType){
            dim += listDimensions(((ListType) type.getElementType()));
        }else{
            dim = 1;
        }

        return dim;
    }

    default List<Integer> sizeByDimension(ListType type){
        List<Integer> sizeByDim = new ArrayList<>();
        if(type.getElementType() instanceof ListType){
            sizeByDim.add(type.getSize().getAsInt());
            sizeByDimension(((ListType) type.getElementType())).stream().forEachOrdered(sizeByDim::add);
        }else{
            sizeByDim.add(type.getSize().getAsInt());
        }

        return sizeByDim;
    }

}
