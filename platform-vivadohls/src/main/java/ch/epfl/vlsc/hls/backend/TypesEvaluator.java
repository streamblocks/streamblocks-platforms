package ch.epfl.vlsc.hls.backend;

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
    VivadoHLSBackend backend();

    default Types types() {
        return backend().types();
    }

    String type(Type type);

    String cltype(Type type);

    default String KernelXmlType(Type type){
        if(type instanceof IntType){
            IntType t = (IntType) type;
            if (t.getSize().isPresent()) {
                int originalSize = t.getSize().getAsInt();
                int targetSize = 8;
                while (originalSize > targetSize) {
                    targetSize = targetSize * 2;
                }
                if(targetSize == 8){
                    return String.format(t.isSigned() ? "char" : "unsigned char");
                }else if(targetSize == 16){
                    return String.format(t.isSigned() ? "short" : "unsigned short");
                }else if(targetSize == 32){
                    return String.format(t.isSigned() ? "int" : "unsigned int");
                }else{
                    return String.format(t.isSigned() ? "long" : "unsigned long");
                }
            } else {
                return t.isSigned() ? "int" : "unsigned int ";
            }
        }else{
           return type(type);
        }
    }

    default String cltype(IntType type){
            if (type.getSize().isPresent()) {
                int originalSize = type.getSize().getAsInt();
                int targetSize = 8;
                while (originalSize > targetSize) {
                    targetSize = targetSize * 2;
                }
                if(targetSize == 8){
                    return String.format(type.isSigned() ? "cl_char" : "cl_uchar");
                }else if(targetSize == 16){
                    return String.format(type.isSigned() ? "cl_short" : "cl_ushort");
                }else if(targetSize == 32){
                    return String.format(type.isSigned() ? "cl_int" : "cl_uint");
                }else{
                    return String.format(type.isSigned() ? "cl_long" : "cl_ulong");
                }
            } else {
                return type.isSigned() ? "cl_int" : "cl_uint ";
            }
    }

    default String cltype(RealType type) {
        switch (type.getSize()) {
            case 32:
                return "cl_float";
            case 64:
                return "cl_double";
            default:
                throw new UnsupportedOperationException("Unknown real type.");
        }
    }

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

    default String  type(ListType type) {

        Type innerType = innerType(type.getElementType());

        return type(innerType);
    }

    default String type(StringType type) {
        return "char *";
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
