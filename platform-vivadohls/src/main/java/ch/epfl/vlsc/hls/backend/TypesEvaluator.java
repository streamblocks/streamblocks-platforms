package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.utils.MathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.*;


import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

@Module
public interface TypesEvaluator {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Types types() {
        return backend().types();
    }

    String type(Type type);

    String cltype(Type type);

    default String KernelXmlType(Type type) {
        if (type instanceof IntType) {
            IntType t = (IntType) type;
            if (t.getSize().isPresent()) {
                int originalSize = t.getSize().getAsInt();
                int targetSize = 8;
                while (originalSize > targetSize) {
                    targetSize = targetSize * 2;
                }
                if (targetSize == 8) {
                    return String.format(t.isSigned() ? "char" : "unsigned char");
                } else if (targetSize == 16) {
                    return String.format(t.isSigned() ? "short" : "unsigned short");
                } else if (targetSize == 32) {
                    return String.format(t.isSigned() ? "int" : "unsigned int");
                } else {
                    return String.format(t.isSigned() ? "long" : "unsigned long");
                }
            } else {
                return t.isSigned() ? "int" : "unsigned int ";
            }
        } else {
            return type(type);
        }
    }


    default Integer bitPerType(Type type) {
        if (type instanceof IntType) {
            IntType t = (IntType) type;
            if (t.getSize().isPresent()) {
                int originalSize = t.getSize().getAsInt();
                int targetSize = 8;
                while (originalSize > targetSize) {
                    targetSize = targetSize * 2;
                }
                return targetSize;
            } else {
                return 32;
            }
        } else {
            return 32;
        }
    }


    default String cltype(IntType type) {
        if (type.getSize().isPresent()) {
            int originalSize = type.getSize().getAsInt();
            int targetSize = 8;
            while (originalSize > targetSize) {
                targetSize = targetSize * 2;
            }
            if (targetSize == 8) {
                return String.format(type.isSigned() ? "cl_char" : "cl_uchar");
            } else if (targetSize == 16) {
                return String.format(type.isSigned() ? "cl_short" : "cl_ushort");
            } else if (targetSize == 32) {
                return String.format(type.isSigned() ? "cl_int" : "cl_uint");
            } else {
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
        if (!backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
           return standardIntegerTypes(type);
        } else {
            return arbitraryIntegerTypes(type);
        }
    }

    default String standardIntegerTypes(IntType type) {
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

    default String arbitraryIntegerTypes(IntType type) {
        if (type.getSize().isPresent()) {
            int size = type.getSize().getAsInt();
            return String.format(type.isSigned() ? "ap_int< %d >" : "ap_uint< %d >", size);
        } else {
            return type.isSigned() ? "ap_int< 32 >" : "ap_uint< 32 >";
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

    /**
     * Return the size in number of bits for a given type.
     *
     * @param type
     * @return
     */
    default int sizeOfBits(Type type) {
        if (type instanceof IntType) {
            IntType t = (IntType) type;
            OptionalInt size = t.getSize();
            if (size.isPresent()) {
                int bitSize = size.getAsInt();
                if (!backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
                    if (bitSize <= 8) {
                        return 8;
                    } else if (bitSize <= 16) {
                        return 16;
                    } else if (bitSize <= 32) {
                        return 32;
                    } else {
                        return 64;
                    }
                } else {
                    return bitSize;
                }
            } else {
                return 32;
            }
        } else if (type instanceof RealType) {
            RealType r = (RealType) type;
            if (r.getSize() == 64) {
                return 64;
            } else {
                return 32;
            }
        } else if (type instanceof ListType) {
            ListType l = (ListType) type;
            if (l.getSize().isPresent()) {
                return l.getSize().getAsInt() * sizeOfBits(l.getElementType());
            } else {
                throw new UnsupportedOperationException("Size of the list should be given.");
            }
        } else if (type instanceof SumType) {
            SumType sum = (SumType) type;
            int size = 0;
            size += MathUtils.countBit(sum.getVariants().size() + 1);

            for (SumType.VariantType variant : sum.getVariants()) {
                for (FieldType field : variant.getFields()) {
                    size += sizeOfBits(field.getType());
                }
            }
            return size;
        } else if (type instanceof ProductType) {
            ProductType product = (ProductType) type;
            int size = 0;
            for (FieldType field : product.getFields()) {
                size += sizeOfBits(field.getType());
            }
            return size;
        } else if (type instanceof BoolType) {
            return 1;
        } else {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            "Unhandled connection type " + type.getClass().toString()));
//            return 32;
        }
    }


}
