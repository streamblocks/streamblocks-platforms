package ch.epfl.vlsc.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.sw.ir.PartitionHandle;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Instance;

import java.util.List;
import java.util.stream.Collectors;

@Module
public interface Instances {
    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Declarations declarations() {
        return backend().declarations();
    }

    default Types types() {
        return backend().types();
    }

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default Statements statements() {
        return backend().statements();
    }

    default ChannelsUtils channelutils() {
        return backend().channelsutils();
    }

    default ExpressionEvaluator expressioneval() {
        return backend().expressionEval();
    }

    default void generateInstance(Instance instance) {
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        String entityClass = entityDecl.getOriginalName();

        List<PartitionHandle.Pair<PortDecl, String>> inputPortNamesTypes = channelutils().getInputPortNamesAndTypes(entityDecl);
        List<PartitionHandle.Pair<PortDecl, String>> outputPortNamesTypes = channelutils().getOutputPortNamesAndTypes(entityDecl);

        String inputPortString = inputPortNamesTypes.stream().map(x -> "%" + x._1 + ": " + x._2 ).collect(Collectors.joining(","));
        String outputPortString = outputPortNamesTypes.stream().map(x -> "%" + x._1 + ": " + x._2 ).collect(Collectors.joining(","));

        emitter().emit("//-- Definition of actor class: %s", entityClass);
        emitter().emit("dfg operator @" + entityClass);
        emitter().emit("\tinputs(%s)", inputPortString);
        emitter().emit("\toutputs(%s)", outputPortString);
        emitter().emit("{");
        emitter().increaseIndentation();

        emitter().emit("- %s - ",entityDecl.getEntity().getClass().toString());

        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
    }

}
