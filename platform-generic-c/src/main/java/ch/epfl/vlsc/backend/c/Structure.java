package ch.epfl.vlsc.backend.c;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.*;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Module
public interface Structure {
    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default Code code() {
        return backend().code();
    }

    default Types types() {
        return backend().types();
    }

    default DefaultValues defVal() { return backend().defaultValues(); }

    default void actorHdr(GlobalEntityDecl decl) {
        String name = backend().instance().get().getInstanceName();
        actorHeader(name, decl.getEntity());
    }

    default void actorDecl(GlobalEntityDecl decl) {
        String name = backend().instance().get().getInstanceName();
        actor(name, decl.getEntity());
    }

    default void actorHeader(String name, Entity entity) {}

    default void actorHeader(String name, ActorMachine actorMachine) {
        actorMachineState(name, actorMachine);
        actorMachineInitHeader(name, actorMachine);
        actorMachineControllerHeader(name, actorMachine);
    }

    default void actor(String name, Entity entity) {}

    default void actor(String name, ActorMachine actorMachine) {
        actorMachineStateInit(name, actorMachine);
        actorMachineInit(name, actorMachine);
        actorMachineTransitions(name, actorMachine);
        actorMachineConditions(name, actorMachine);
        actorMachineController(name, actorMachine);
    }

    default void actorMachineControllerHeader(String name, ActorMachine actorMachine) {
        backend().controllers().emitControllerHeader(name, actorMachine);
        emitter().emit("");
    }
    default void actorMachineController(String name, ActorMachine actorMachine) {
        backend().controllers().emitController(name, actorMachine);
        emitter().emit("");
        emitter().emit("");
    }

    default void actorMachineInitHeader(String name, ActorMachine actorMachine) {
        String selfParameter = name + "_state *self";
        List<String> parameters = getEntityInitParameters(selfParameter, actorMachine);
        emitter().emit("void %s_init_actor(%s);", name, String.join(", ", parameters));
        emitter().emit("");
    }

    default void actorMachineInit(String name, ActorMachine actorMachine) {
        String selfParameter = name + "_state *self";
        List<String> parameters = getEntityInitParameters(selfParameter, actorMachine);
        emitter().emit("void %s_init_actor(%s) {", name, String.join(", ", parameters));
        emitter().increaseIndentation();
        emitter().emit("self->program_counter = 0;");
        emitter().emit("");

        emitter().emit("// parameters");
        actorMachine.getValueParameters().forEach(d -> {
            emitter().emit("self->%s = %1$s;", backend().variables().declarationName(d));
        });
        emitter().emit("");

        emitter().emit("// input ports");
        actorMachine.getInputPorts().forEach(p -> {
            emitter().emit("self->%s_channel = %1$s_channel;", p.getName());
        });
        emitter().emit("");

        emitter().emit("// output ports");
        actorMachine.getOutputPorts().forEach(p -> {
            emitter().emit("self->%s_channels = %1$s_channels;", p.getName());
        });
        emitter().emit("");

        emitter().emit("// init persistent scopes");
        int i = 0;
        for (Scope s : actorMachine.getScopes()) {
            if (s.isPersistent()) {
                emitter().emit("%s_init_scope_%d(self);", name, i);
            }
            i = i + 1;
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
        emitter().emit("");
    }

    default List<String> getEntityInitParameters(String selfParameter, Entity actorMachine) {
        List<String> parameters = new ArrayList<>();
        parameters.add(selfParameter);
        actorMachine.getValueParameters().forEach(d -> {
            parameters.add(code().declaration(types().declaredType(d), backend().variables().declarationName(d)));
        });
        actorMachine.getInputPorts().forEach(p -> {
            String type = backend().channels().targetEndTypeSize(new Connection.End(Optional.of(backend().instance().get().getInstanceName()), p.getName()));
            parameters.add(String.format("channel_%s *%s_channel", type, p.getName()));
        });
        actorMachine.getOutputPorts().forEach(p -> {
            Connection.End source = new Connection.End(Optional.of(backend().instance().get().getInstanceName()), p.getName());
            String type = backend().channels().sourceEndTypeSize(source);
            parameters.add(String.format("channel_list_%s %s_channels", type, p.getName()));
        });
        return parameters;
    }

    default void actorMachineTransitions(String name, ActorMachine actorMachine) {
        int i = 0;
        for (Transition transition : actorMachine.getTransitions()) {
            emitter().emit("static void %s_transition_%d(%s_state *self) {", name, i, name);
            emitter().increaseIndentation();
            transition.getBody().forEach(code()::execute);
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
            emitter().emit("");
            i++;
        }
    }

    default void actorMachineConditions(String name, ActorMachine actorMachine) {
        int i = 0;
        for (Condition condition : actorMachine.getConditions()) {
            emitter().emit("static _Bool %s_condition_%d(%s_state *self) {", name, i, name);
            emitter().increaseIndentation();
            emitter().emit("return %s;", evaluateCondition(condition));
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
            emitter().emit("");
            i++;
        }
    }

    String evaluateCondition(Condition condition);

    default String evaluateCondition(PredicateCondition condition) {
        return code().evaluate(condition.getExpression());
    }

    default String evaluateCondition(PortCondition condition) {
        if (condition.isInputCondition()) {
            return String.format("channel_has_data_%s(self->%s_channel, %d)", code().inputPortTypeSize(condition.getPortName()), condition.getPortName().getName(), condition.N());
        } else {
            return String.format("channel_has_space_%s(self->%s_channels, %d)", code().outputPortTypeSize(condition.getPortName()), condition.getPortName().getName(), condition.N());
        }
    }


    default void actorMachineStateInit(String name, ActorMachine actorMachine) {
        int i = 0;
        for (Scope scope : actorMachine.getScopes()) {
            emitter().emit("static void %s_init_scope_%d(%s_state *self) {", name, i, name);
            emitter().increaseIndentation();
            for (VarDecl var : scope.getDeclarations()) {
                Type type = types().declaredType(var);
                if (var.isExternal() && type instanceof CallableType) {
                    String wrapperName = backend().callables().externalWrapperFunctionName(var);
                    String variableName = backend().variables().declarationName(var);
                    String t = backend().callables().mangle(type).encode();
                    emitter().emit("self->%s = (%s) { *%s, NULL };", variableName, t, wrapperName);
                } else if (var.getValue() != null) {
                    emitter().emit("{");
                    emitter().increaseIndentation();
                    code().copy(types().declaredType(var), "self->" + backend().variables().declarationName(var), types().type(var.getValue()), code().evaluate(var.getValue()));
                    emitter().decreaseIndentation();
                    emitter().emit("}");
                }
            }
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
            emitter().emit("");
            i++;
        }
    }


    default void actorMachineState(String name, ActorMachine actorMachine) {
        emitter().emit("typedef struct {");
        emitter().increaseIndentation();

        emitter().emit("int program_counter;");
        emitter().emit("");

        emitter().emit("// parameters");
        for (VarDecl param : actorMachine.getValueParameters()) {
            String decl = code().declaration(types().declaredType(param), backend().variables().declarationName(param));
            emitter().emit("%s;", decl);
        }
        emitter().emit("");

        emitter().emit("// input ports");
        for (PortDecl input : actorMachine.getInputPorts()) {
            String type = backend().channels().targetEndTypeSize(new Connection.End(Optional.of(backend().instance().get().getInstanceName()), input.getName()));
            emitter().emit("channel_%s *%s_channel;", type, input.getName());
        }
        emitter().emit("");

        emitter().emit("// output ports");
        for (PortDecl output : actorMachine.getOutputPorts()) {
            Connection.End source = new Connection.End(Optional.of(backend().instance().get().getInstanceName()), output.getName());
            String type = backend().channels().sourceEndTypeSize(source);
            emitter().emit("channel_list_%s %s_channels;", type, output.getName());
        }
        emitter().emit("");

        int i = 0;
        for (Scope scope : actorMachine.getScopes()) {
            emitter().emit("// scope %d", i);
            backend().callables().declareEnvironmentForCallablesInScope(scope);
            for (VarDecl var : scope.getDeclarations()) {
                String decl = code().declaration(types().declaredType(var), backend().variables().declarationName(var));
                emitter().emit("%s;", decl);
            }
            emitter().emit("");
            i++;
        }
        emitter().decreaseIndentation();
        emitter().emit("} %s_state;", name);
        emitter().emit("");
        emitter().emit("");
    }

    default void actorDecls(List<GlobalEntityDecl> entityDecls) {
        entityDecls.forEach(backend().structure()::actorDecl);
    }
}
