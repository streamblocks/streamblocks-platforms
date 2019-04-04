package ch.epfl.vlsc.platformutils.utils;

import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;

import java.util.OptionalInt;

public class TypeUtils {

    public static int sizeOfBits(Type type) {
        if (type instanceof IntType) {
            IntType t = (IntType) type;
            OptionalInt size = t.getSize();
            if (size.isPresent()) {
                int bitSize = size.getAsInt();
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
                return 32;
            }
        } else {
            return 32;
        }
    }

}
