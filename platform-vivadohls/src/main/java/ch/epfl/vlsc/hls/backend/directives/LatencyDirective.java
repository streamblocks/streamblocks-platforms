package ch.epfl.vlsc.hls.backend.directives;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueLong;

import java.util.List;

public class LatencyDirective implements Directive {

    private final String name;

    private final boolean hasMin;

    private final boolean hasMax;

    private final int min;

    private final int max;

    public LatencyDirective(boolean hasMin, int min, boolean hasMax, int max) {
        this.name = "LATENCY";
        this.hasMin = hasMin;
        this.min = min;
        this.hasMax = hasMax;
        this.max = max;
    }

    public static Directive parse(Interpreter interpreter, Annotation annotation) {
        Environment environment = new Environment();

        boolean hasMin = false;

        boolean hasMax = false;

        int min = -1;

        int max = -1;

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
        }
        return new LatencyDirective(hasMin, min, hasMax, max);
    }

    @Override
    public String toString() {
        return name + (hasMin ? " min=" + min : "") + (hasMax ? " max=" + max : "");
    }

}
