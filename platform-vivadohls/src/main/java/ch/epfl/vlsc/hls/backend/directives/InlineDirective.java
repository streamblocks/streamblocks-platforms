package ch.epfl.vlsc.hls.backend.directives;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueBool;

import java.util.List;

public class InlineDirective implements Directive {

    private final String name;

    private final boolean region;

    private final boolean recursive;

    private final boolean off;

    public InlineDirective(boolean region, boolean recursive, boolean off) {
        this.name = "INLINE";
        this.region = region;
        this.recursive = recursive;
        this.off = off;
    }


    public static Directive parse(Interpreter interpreter, Annotation annotation) {
        Environment environment = new Environment();

        boolean region = false;
        boolean recursive = false;
        boolean off = false;

        List<AnnotationParameter> parameters = annotation.getParameters();
        for (AnnotationParameter parameter : parameters) {
            if (parameter.getName().equals("region")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    region = vBool.bool();
                }
            }
            if (parameter.getName().equals("recursive")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    recursive = vBool.bool();
                }
            }
            if (parameter.getName().equals("off")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    off = vBool.bool();
                }
            }
        }
        return new InlineDirective(region, recursive, off);
    }

    @Override
    public String toString() {
        if (off) {
            return name + " off";
        } else {
            return name + (region ? " region" : "") + (recursive ? " recursive" : "");
        }
    }
}
