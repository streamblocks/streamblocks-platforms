package ch.epfl.vlsc.hls.backend.directives;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueLong;

import java.util.List;

public class LoopTripCountDirective implements Directive {
    private final String name;

    private final boolean hasMin;

    private final boolean hasMax;

    private final boolean hasAverage;

    private final int min;

    private final int max;

    private final int average;

    public LoopTripCountDirective(boolean hasMin, int min, boolean hasMax, int max, boolean hasAverage, int average) {
        this.name = "LOOP_TRIPCOUNT";
        this.hasMin = hasMin;
        this.min = min;
        this.hasMax = hasMax;
        this.max = max;
        this.hasAverage = hasAverage;
        this.average = average;
    }

    public static Directive parse(Interpreter interpreter, Annotation annotation) {
        Environment environment = new Environment();

        boolean hasMin = false;

        boolean hasMax = false;

        boolean hasAverage = false;

        int min = -1;

        int max = -1;

        int average = -1;

        List<AnnotationParameter> parameters = annotation.getParameters();
        for (AnnotationParameter parameter : parameters) {
            if (parameter.getName().equals("min")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueLong) {
                    hasMin = true;
                    ValueLong vLong = (ValueLong) value;
                    min = (int) vLong.value();
                }
            }

            if (parameter.getName().equals("max")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueLong) {
                    hasMax = true;
                    ValueLong vLong = (ValueLong) value;
                    max = (int) vLong.value();
                }
            }

            if (parameter.getName().equals("avg")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueLong) {
                    hasAverage = true;
                    ValueLong vLong = (ValueLong) value;
                    average = (int) vLong.value();
                }
            }

        }
        return new LoopTripCountDirective(hasMin, min, hasMax, max, hasAverage, average);
    }

    @Override
    public String toString() {
        return name + (hasMin ? " min=" + min : "") + (hasMax ? " max=" + max : "") + (hasAverage ? " avg=" + average : "");
    }
}
