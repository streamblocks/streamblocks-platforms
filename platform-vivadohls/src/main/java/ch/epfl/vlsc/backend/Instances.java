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
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.network.Instance;

import java.nio.file.Path;

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

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".cpp");
        emitter().open(instanceTarget);

        // -- Includes
        defineIncludes();

        // -- Instance State
        instanceState(instanceName, entity);

        // -- EOF
        emitter().close();

        // -- Clear boxes
        backend().entitybox().clear();
        backend().instancebox().clear();
    }

    /*
     * Instance headers
     */

    default void defineIncludes(){
        backend().includeSystem("ap_int.h");
        backend().includeSystem("hls_stream.h");
        backend().includeSystem("stdint.h");
        emitter().emitNewLine();
    }

    void instanceState(String instanceName, Entity entity);

    default void instanceState(String instanceName, CalActor actor){
        emitter().emit("// -- Instance state");
        emitter().emit("typedef struct{");
        emitter().increaseIndentation();
        for(VarDecl var : actor.getVarDecls()){
            if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                // -- Do nothing
            } else {
                String decl = declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                emitter().emit("%s;", decl);
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("} ActorInstance_%s;", backend().instaceQID(instanceName, "_"));
    }

}
