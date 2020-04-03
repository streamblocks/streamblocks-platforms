package ch.epfl.vlsc.platformutils.utils;

import java.util.AbstractMap;
import java.util.Map;

public class Pair {
    public static <T, U> Map.Entry<T, U> of(T first, U second) {
        return new AbstractMap.SimpleEntry<>(first, second);
    }
}
