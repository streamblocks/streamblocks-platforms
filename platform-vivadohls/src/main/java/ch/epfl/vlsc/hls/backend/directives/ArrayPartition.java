package ch.epfl.vlsc.hls.backend.directives;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.meta.interp.value.ValueLong;
import se.lth.cs.tycho.meta.interp.value.ValueString;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public class ArrayPartition implements Directive {

    private final String name;

    private final Optional<String> variable;

    private final Optional<String> type;

    private final OptionalLong factor;

    private final OptionalLong dimension;

    public ArrayPartition(Optional<String> variable, Optional<String> type, OptionalLong factor, OptionalLong dimension) {
        this.name = "ARRAY_PARTITION";
        this.variable = variable;
        this.type = type;
        this.factor = factor;
        this.dimension = dimension;
    }


    public static Directive parse(VivadoHLSBackend backend, Annotation annotation) {

        Interpreter interpreter = backend.interpreter();
        Environment environment = new Environment();

        Optional<String> variable = Optional.empty();

        Optional<String> type = Optional.empty();

        OptionalLong factor = OptionalLong.empty();

        OptionalLong dimension = OptionalLong.of(1);

        List<AnnotationParameter> parameters = annotation.getParameters();
        for (AnnotationParameter parameter : parameters) {
            if (parameter.getName().equals("variable")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueString) {
                    ValueString val = (ValueString) value;

                    Instance instance = backend.instancebox().get();
                    Entity entity = backend.entitybox().get();
                    ActorMachine am = (ActorMachine) entity;
                    Optional<LocalVarDecl> decl = am.getScopes().stream()
                            .map(Scope::getDeclarations)
                            .flatMap(ImmutableList::stream).filter(d -> d.getOriginalName().equals(val.string().replaceAll("^\"+|\"+$", ""))).findAny();
                    if (decl.isPresent()) {
                        variable = Optional.of(String.format("i_%s.%s", instance.getInstanceName(), backend.variables().declarationName(decl.get())));
                    }
                }
            }

            if (parameter.getName().equals("kind")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueString) {
                    ValueString val = (ValueString) value;
                    type = Optional.of(val.string().replaceAll("^\"+|\"+$", ""));
                }
            }

            if (parameter.getName().equals("factor")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueLong) {
                    ValueLong val = (ValueLong) value;
                    factor = OptionalLong.of(val.value());
                }
            }
            if (parameter.getName().equals("dim")) {
                Value value = interpreter.eval(parameter.getExpression(), environment);
                if (value instanceof ValueLong) {
                    ValueLong val = (ValueLong) value;
                    dimension = OptionalLong.of(val.value());
                }
            }
        }
        return new ArrayPartition(variable, type, factor, dimension);
    }

    @Override
    public String toString() {
        return name + (variable.isPresent() ? " variable=" + variable.get() : "") + (type.isPresent() ? " " + type.get() + " " : "") + (factor.isPresent() ? " factor=" + factor.getAsLong() : "") + (dimension.isPresent() ? " dim=" + dimension.getAsLong() : "");
    }
}
