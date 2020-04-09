package ch.epfl.vlsc.sw.backend;


import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.ir.PartitionLink;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.nio.file.Path;

/**
 * An interface to generate the C code for the PartitionLink instance in a heterogeneous network.
 */
@Module
public interface PLink {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }
    default Types types() {
        return backend().types();
    }

    default void unimpl() {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,"plink " +
                stackTrace[2].getMethodName() + " method not implemented!"));
    }

    default void generatePLink(Instance instance) throws CompilationException{

        // -- Add the instance to instancebox for later use
        backend().instancebox().set(instance);

        // -- Get the PartitionLink Entity
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add the entity to the entitybox for later use
        backend().entitybox().set(entity);

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        Path instanceTarget;
        // -- Target file Path
        if (backend().context().getConfiguration().get(PlatformSettings.runOnNode)) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Node platform heterogeneous code execution is not" +
                            "supported yet"));
        } else {
            instanceTarget = PathUtils.getTargetCodeGenSource(backend().context())
                    .resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".c");
        }

        emitter().open(instanceTarget);

        // -- Includes
        defineIncludes();

        // -- Ports IO
        portsIO(entity);

        // -- Context
        actionContext(instanceName, entity);


        // -- State
        instanceState(instanceName, entity);

        // -- Port description
        portDescription(instanceName, entity);

        // -- State variable description
        stateVariableDescription(instanceName, entity);

        // -- Port rate
        portRate(instanceName, entity);

        // -- Use / Defines
        useDefines(instanceName, entity);

        // -- Transitions descriptions
        transitionDescription(instanceName, entity);

        // -- Used variables in conditions
        usedVariablesInConditions(instanceName, entity);

        // -- Conditions description
        conditionDescription(instanceName, entity);

        // -- ART ActorClass
        actorClass(instanceName, entity);

        // -- Transitions definition
        transitionDefinitions(instanceName, entity);

        // -- Constructor
        constructorDefinition(instanceName, entity);

        // -- Destructor
        destructorDefinition(instanceName, entity);

        // -- Scheduler FSM
        scheduler(instanceName, entity);

        // -- EOF
        emitter().close();

        // -- clear boxes
        backend().instancebox().clear();
        backend().entitybox().clear();
    }

    default void defineIncludes() { unimpl(); }
    default void portsIO(Entity entity) { unimpl(); }
    default void actionContext(String name, Entity entity) { unimpl(); }
    default void instanceState(String name, Entity entity) { unimpl(); }
    default void portDescription(String name, Entity entity) { unimpl(); }
    default void stateVariableDescription(String name, Entity entity) { unimpl(); }
    default void portRate(String name, Entity entity) { unimpl(); }
    default void useDefines(String name, Entity entity) { unimpl(); }
    default void transitionDescription(String name, Entity entity) { unimpl(); }
    default void usedVariablesInConditions(String name, Entity entity) { unimpl(); }
    default void conditionDescription(String name, Entity entity) { unimpl(); }
    default void actorClass(String name, Entity entity) { unimpl(); }
    default void transitionDefinitions(String name, Entity entity) { unimpl(); }
    default void constructorDefinition(String name, Entity entity) { unimpl(); }
    default void destructorDefinition(String name, Entity entity) { unimpl(); }
    default void scheduler(String name, Entity entity) { unimpl(); }

}
