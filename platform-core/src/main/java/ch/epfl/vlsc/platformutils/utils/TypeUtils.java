package ch.epfl.vlsc.platformutils.utils;

import se.lth.cs.tycho.type.*;

import java.util.OptionalInt;

public class TypeUtils {

    public static int sizeOfBits(Type type) {
        return sizeOfBits(type, true);
    }

    /**
     * Return the size in number of bits for a given type.
     *
     * @param type
     * @param byteSize
     * @return
     */
    public static int sizeOfBits(Type type, boolean byteSize) {
        if (type instanceof IntType) {
            IntType t = (IntType) type;
            OptionalInt size = t.getSize();
            if (size.isPresent()) {
                int bitSize = size.getAsInt();
                if (byteSize) {
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
        } else {
            return 32;
        }
    }
}