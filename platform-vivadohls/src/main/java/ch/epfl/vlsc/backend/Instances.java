package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.network.Instance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Module
public interface Instances {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Types types() {
        return backend().types();
    }

    default Declarations declarations() {
        return backend().declarations();
    }


    default void generateInstance(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add entity to box
        backend().entitybox().set(entity);

        // -- Generate Source
        generateSource(instance);

        // -- Generate Header
        generateHeader(instance);

        // -- Clear boxes
        backend().entitybox().clear();
        backend().instancebox().clear();
    }


    default void generateSource(Instance instance) {
        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".cpp");
        emitter().open(instanceTarget);

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Includes
        defineIncludes(false);

        // -- Static call of instance
        staticCallofInstance(instance);


        // -- EOF
        emitter().close();
    }


    default void staticCallofInstance(Instance instance) {
        emitter().emit("void %s(%s) {", instance.getInstanceName(), entityPorts());
        emitter().increaseIndentation();

        String className = backend().instaceQID(instance.getInstanceName(), "_");
        emitter().emit("static %s i_%s;", className, instance.getInstanceName());
        emitter().emitNewLine();

        Entity entity = backend().entitybox().get();
        List<String> ports = entity.getInputPorts().stream().map(PortDecl::getName).collect(Collectors.toList());
        ports.addAll(entity.getOutputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
        if (entity instanceof CalActor) {
            CalActor actor = (CalActor) entity;
            if (actor.getProcessDescription() != null) {
                if (actor.getProcessDescription().isRepeated()) {
                    emitter().emit("i_%s(%s);", instance.getInstanceName(), String.join(", ", ports));
                } else {
                    emitter().emit("bool has_executed = false;");
                    emitter().emitNewLine();
                    emitter().emit("if (!has_executed) {");
                    emitter().increaseIndentation();

                    emitter().emit("i_%s(%s);", instance.getInstanceName(), String.join(", ", ports));
                    emitter().emit("has_executed = true;");

                    emitter().decreaseIndentation();
                    emitter().emit("}");
                }
            } else {
                //throw new UnsupportedOperationException("Actors is not a Process.");
            }
        } else {
            emitter().emit("i_%s(%s);", instance.getInstanceName(), String.join(", ", ports));
        }


        emitter().decreaseIndentation();
        emitter().emit("}");
    }


    default void generateHeader(Instance instance) {
        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenInclude(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".h");
        emitter().open(instanceTarget);

        // -- Entity
        Entity entity = backend().entitybox().get();

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Includes
        defineIncludes(true);

        // -- Instance State
        instanceClass(instanceName, entity);

        // -- Callables
        // -- Todo

        // -- Top of Instance
        topOfInstance(instanceName, entity);

        // -- EOF
        emitter().close();
    }


    /*
     * Instance headers
     */

    default void defineIncludes(boolean isHeader) {
        if (isHeader) {
            backend().includeSystem("ap_int.h");
            backend().includeSystem("hls_stream.h");
            backend().includeSystem("stdint.h");
            backend().includeUser("globals.h");
        } else {
            Instance instance = backend().instancebox().get();
            String headerName = backend().instaceQID(instance.getInstanceName(), "_") + ".h";

            backend().includeUser(headerName);
        }
        emitter().emitNewLine();
    }

    void instanceClass(String instanceName, Entity entity);

    default void instanceClass(String instanceName, CalActor actor) {

        emitter().emit("// -- Instance Class");
        String className = backend().instaceQID(instanceName, "_");
        emitter().emit("class %s {", className);

        // -- Private
        if (!actor.getVarDecls().isEmpty()) {
            emitter().emit("private:");
            emitter().increaseIndentation();

            for (VarDecl var : actor.getVarDecls()) {
                if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                    // -- Do nothing
                } else {
                    String decl = declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                    if (var.getValue() != null) {
                        emitter().emit("%s = %s;", decl, backend().expressioneval().evaluate(var.getValue()));
                    } else {
                        emitter().emit("%s;", decl);
                    }
                }
            }
        }

        emitter().decreaseIndentation();

        // -- Public
        emitter().emit("public:");
        emitter().increaseIndentation();

        emitter().emit("void operator()(%s);", entityPorts());

        emitter().decreaseIndentation();

        emitter().emit("};");
        emitter().emitNewLine();

    }

    default String entityPorts() {
        Entity entity = backend().entitybox().get();
        List<String> ports = new ArrayList<>();
        for (PortDecl port : entity.getInputPorts()) {
            ports.add(backend().declarations().portDeclaration(port));
        }

        for (PortDecl port : entity.getOutputPorts()) {
            ports.add(backend().declarations().portDeclaration(port));
        }

        return String.join(", ", ports);
    }


    /*
     * Top of Instance
     */

    void topOfInstance(String instanceName, Entity entity);

    default void topOfInstance(String instanceName, CalActor actor) {
        if (actor.getProcessDescription() != null) {
            String className = backend().instaceQID(instanceName, "_");
            emitter().emit("void %s::operator()(%s) {", className, entityPorts());
            emitter().emit("#pragma HLS INLINE");
            emitter().increaseIndentation();
            actor.getProcessDescription().getStatements().forEach(backend().statements()::execute);
            emitter().decreaseIndentation();
            emitter().emit("}");

        } else {
            //throw new UnsupportedOperationException("Actors is not a Process.");
        }
    }


}
