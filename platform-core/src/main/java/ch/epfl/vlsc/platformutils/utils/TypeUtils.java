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
                return size.getAsInt();
            } else {
                return 32;
            }
        } else {
            return 32;
        }
    }

}
