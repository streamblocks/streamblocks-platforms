package ch.epfl.vlsc.phase;

import ch.epfl.vlsc.transformation.cal2am.CalToAmHw;
import se.lth.cs.tycho.attribute.ConstantEvaluator;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.Transformations;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.settings.Setting;
import se.lth.cs.tycho.transformation.cal2am.KnowledgeRemoval;

import java.util.List;

public class CalToAmHwPhase implements Phase {
    @Override
    public String getDescription() {
        return "Translates all Cal actors to actor machines for hardware Synthesis";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) {
        return Transformations.transformEntityDecls(task, decl -> {
            if (decl.getEntity() instanceof CalActor) {
                CalActor actor = (CalActor) decl.getEntity();
                if(actor.getProcessDescription() == null){
                    CalToAmHw translator = new CalToAmHw(actor, context.getConfiguration(), task.getModule(ConstantEvaluator.key));
                    return decl.withEntity(translator.buildActorMachine());
                }else{
                    return decl;
                }
            } else {
                return decl;
            }
        });
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(
                KnowledgeRemoval.forgetOnExec,
                KnowledgeRemoval.forgetOnWait
        );
    }
}
