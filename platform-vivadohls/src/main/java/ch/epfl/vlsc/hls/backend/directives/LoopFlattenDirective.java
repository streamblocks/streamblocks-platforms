package ch.epfl.vlsc.hls.backend.directives;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueBool;

import java.util.List;

public class LoopFlattenDirective implements Directive {

    private final String name;

    private final boolean off;

    public LoopFlattenDirective(boolean force) {
        this.name = "LOOP_FLATTEN";
        this.off = force;
    }

    public static Directive parse(Interpreter interpreter, Annotation annotation) {
        Environment environment = new Environment();

        boolean force = false;

        List<AnnotationParameter> parameters = annotation.getParameters();
        for (AnnotationParameter parameter : parameters) {
            if (parameter.getName().equals("off")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    force = vBool.bool();
                }
            }
        }

        return new LoopFlattenDirective(force);
    }

    public String getName() {
        return name;
    }

    public boolean hasParameters() {
        return true;
    }

    @Override
    public String toString() {
        return name + (off ? " off" : "");
    }
}

