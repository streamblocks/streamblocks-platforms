package se.lth.cs.mlir.backend.util;

import java.util.ArrayList;
import java.util.List;

public class PrintStringResult {
    public final String formatString;
    public final List<String> ssaValues;
    public final List<String> types;

    public PrintStringResult(String formatString, List<String> ssaValues, List<String> types) {
        this.formatString = formatString;
        this.ssaValues = ssaValues;
        this.types = types;
    }

    public PrintStringResult(String formatString, String ssaValue, String type) {
        this.formatString = formatString;
        this.ssaValues = new ArrayList<>();
        this.ssaValues.add(ssaValue);
        this.types = new ArrayList<>();
        this.types.add(type);
    }

    public PrintStringResult(String formatString) {
        this.formatString = formatString;
        this.ssaValues = new ArrayList<>();
        this.types = new ArrayList<>();
    }

    public PrintStringResult() {
        this.formatString = "";
        this.ssaValues = new ArrayList<>();
        this.types = new ArrayList<>();
    }

    public static PrintStringResult merge(PrintStringResult lhs, PrintStringResult rhs){
        String mergedValue = lhs.formatString + rhs.formatString;

        List<String> mergedSSA = new ArrayList<>(lhs.ssaValues);
        mergedSSA.addAll(rhs.ssaValues);

        List<String> mergedTypes = new ArrayList<>(lhs.types);
        mergedTypes.addAll(rhs.types);

        return new PrintStringResult(mergedValue, mergedSSA, mergedTypes);
    }
}
