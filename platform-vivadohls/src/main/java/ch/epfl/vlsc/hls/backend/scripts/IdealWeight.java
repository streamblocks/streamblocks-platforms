package ch.epfl.vlsc.hls.backend.scripts;


import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Module
public interface IdealWeight {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void emitIdealWeights() {
        Network network = backend().task().getNetwork();

        String name = backend().task().getIdentifier().toString();
        Path idealWeights = PathUtils.getAuxiliary(backend().context()).resolve(name + "_ideal.exdf");
        emitter().open(idealWeights);

        emitter().emit("<?xml version=\"1.0\" ?>");
        emitter().emit("<network name=\"%s\">", backend().task().getIdentifier().toString());
        emitter().increaseIndentation();

        for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            ActorMachine actor = (ActorMachine) entityDecl.getEntity();

            emitter().emit("<actor id=\"%s\">", instance.getInstanceName());
            emitter().increaseIndentation();


            for (Transition transition : actor.getTransitions()) {
                Map<Port, Integer> inputPortRate = transition.getInputRates();
                Map<Port, Integer> outputPortRate = transition.getOutputRates();

                int maxInput = inputPortRate.values().stream().mapToInt(v -> v).max().orElse(1);
                int maxOutput = outputPortRate.values().stream().mapToInt(v -> v).max().orElse(1);

                int idealLatency = Integer.max(maxInput, maxOutput);
                String actionName = "unknown";

                Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
                if (annotation.isPresent()) {
                    actionName = ((ExprLiteral) annotation.get().getParameters().get(0).getExpression()).getText();
                }

                emitter().emit("<action id=\"%s\" clockcycles=\"%2$s\" clockcycles-min=\"%2$s\" clockcycles-max=\"%2$s\"/>", actionName, idealLatency);

            }

            emitter().decreaseIndentation();
            emitter().emit("</actor>");
        }
        emitter().decreaseIndentation();
        emitter().emit("</network>");
        emitter().close();
    }
}
