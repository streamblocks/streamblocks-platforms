package ch.epfl.vlsc.hls.backend.directives;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueBool;
import se.lth.cs.tycho.meta.interp.value.ValueLong;

import java.util.List;

public class PipelineDirective implements Directive {

    public final String name;

    public final boolean hasInitiationInterval;

    public final int initiationInterval;

    public final boolean flushing;

    public final boolean rewinding;

    public final boolean disable;

    public PipelineDirective(boolean hasInitiationInterval, int initiationInterval, boolean flushing, boolean rewinding, boolean disable) {
        this.name = "PIPELINE";
        this.hasInitiationInterval = hasInitiationInterval;
        this.initiationInterval = initiationInterval;
        this.flushing = flushing;
        this.rewinding = rewinding;
        this.disable = disable;
    }

    public static Directive parse(Interpreter interpreter, Annotation annotation) {
        Environment environment = new Environment();

        boolean hasInitiationInterval = false;

        int initiationInterval = -1;

        boolean flushing = false;

        boolean rewinding = false;

        boolean disable = false;

        List<AnnotationParameter> parameters = annotation.getParameters();
        for (AnnotationParameter parameter : parameters) {

            if (parameter.getName().equals("ii")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueLong) {
                    hasInitiationInterval = true;
                    ValueLong vLong = (ValueLong) value;
                    initiationInterval = (int) vLong.value();
                }
            }

            if (parameter.getName().equals("flushing")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    flushing = vBool.bool();
                }
            }

            if (parameter.getName().equals("rewinding")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    rewinding = vBool.bool();
                }
            }

            if (parameter.getName().equals("disable")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    disable = vBool.bool();
                }
            }
        }
        return new PipelineDirective(hasInitiationInterval, initiationInterval, flushing, rewinding, disable);
    }



    public String getName() {
        return "PIPELINE";
    }

    @Override
    public String toString() {
        if (disable) {
            return name + " off";
        } else {
            return name + (hasInitiationInterval ? (" II=" + initiationInterval) : "") + (flushing ? " enable_flush" : "");
        }
    }


}
