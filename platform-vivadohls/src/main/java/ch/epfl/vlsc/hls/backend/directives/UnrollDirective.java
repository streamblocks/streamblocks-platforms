package ch.epfl.vlsc.hls.backend.directives;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueBool;
import se.lth.cs.tycho.meta.interp.value.ValueLong;

import java.util.List;

public class UnrollDirective implements Directive {

    private final String name;

    private final boolean skipExitCheck;

    private final boolean hasFactor;

    private final int factor;

    private final boolean region;

    public UnrollDirective(boolean skipExitCheck, boolean hasFactor, int factor, boolean region) {
        this.name = "UNROLL";
        this.skipExitCheck = skipExitCheck;
        this.hasFactor = hasFactor;
        this.factor = factor;
        this.region = region;
    }

    public static Directive parse(Interpreter interpreter, Annotation annotation) {
        Environment environment = new Environment();

        boolean skipExitCheck = false;
        boolean hasFactor = false;
        int factor = -1;
        boolean region = false;

        List<AnnotationParameter> parameters = annotation.getParameters();
        for (AnnotationParameter parameter : parameters) {
            if (parameter.getName().equals("skip_exit_check")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    skipExitCheck = vBool.bool();
                }
            }
            if (parameter.getName().equals("factor")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueLong) {
                    hasFactor = true;
                    ValueLong vLong = (ValueLong) value;
                    factor = (int) vLong.value();
                }
            }
            if (parameter.getName().equals("region")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    region = vBool.bool();
                }
            }
        }
        return new UnrollDirective(skipExitCheck, hasFactor, factor, region);
    }


    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name + (skipExitCheck ? " skip_exit_check" : "") + (hasFactor ? " factor=" + factor : "") + (region ? " region" : "");
    }
}
