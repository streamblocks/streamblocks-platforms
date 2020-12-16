package ch.epfl.vlsc.hls.backend.directives;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueBool;

import java.util.List;

public class LoopMergeDirective implements Directive {

    private final String name;

    private final boolean force;

    public LoopMergeDirective(boolean force) {
        this.name = "LOOP_MERGE";
        this.force = force;
    }

    public static Directive parse(Interpreter interpreter, Annotation annotation) {
        Environment environment = new Environment();

        boolean force = false;

        List<AnnotationParameter> parameters = annotation.getParameters();
        for (AnnotationParameter parameter : parameters) {
            if (parameter.getName().equals("force")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueBool) {
                    ValueBool vBool = (ValueBool) value;
                    force = vBool.bool();
                }
            }
        }

        return new LoopMergeDirective(force);
    }

    @Override
    public String toString() {
        return name + (force ? " force" : "");
    }
}
