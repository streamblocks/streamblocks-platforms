package ch.epfl.vlsc.platformutils.utils;

import java.util.Collection;
import java.util.Map;

public class MathUtils {
    public static int nearestPowTwo(Integer num) {
        int n = num > 0 ? num - 1 : 0;

        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        n++;

        return n;
    }

    public static int log2Ceil(Integer bits) {
        int log = 0;
        if ((bits & 0xffff0000) != 0) {
            bits >>>= 16;
            log = 16;
        }
        if (bits >= 256) {
            bits >>>= 8;
            log += 8;
        }
        if (bits >= 16) {
            bits >>>= 4;
            log += 4;
        }
        if (bits >= 4) {
            bits >>>= 2;
            log += 2;
        }
        return log + (bits >>> 1);
    }


    /**
     * Returns <code>true</code> if the given integer is a power of two.
     *
     * @param n an integer
     * @return <code>true</code> if the given integer is a power of two
     */
    public static boolean isPowerOfTwo(int n) {
        return (n > 0) && (n & (n - 1)) == 0;
    }

    public static int countBit(int n) {
        return (int) Math.floor(Math.log(n) / Math.log(2)) + 1;
    }

    /**
     * Sum the values of a {@link Long} map
     *
     * @param data
     * @return
     */
    public static Long sumLong(Map<?, Long> data) {
        return sumLong(data.values());
    }

    /**
     * Sum the values of a {@link Long} collection
     *
     * @param data
     * @return
     */
    public static Long sumLong(Collection<Long> data) {
        long result = 0;
        for (long v : data) {
            result += v;
        }
        return result;
    }

}
