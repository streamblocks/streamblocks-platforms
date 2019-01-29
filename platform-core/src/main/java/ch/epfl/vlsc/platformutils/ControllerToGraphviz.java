package ch.epfl.vlsc.platformutils;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Condition;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.PredicateCondition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;
import se.lth.cs.tycho.ir.network.Instance;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.multij.BindingKind.LAZY;

public class ControllerToGraphviz {


    private ActorMachine actorMachine;
    private String name;
    private Path path;

    public ControllerToGraphviz(GlobalEntityDecl actor, String name, Path path) {
        this.name = name;
        this.path = path;

        if (actor.getEntity() instanceof ActorMachine) {
            actorMachine = (ActorMachine) actor.getEntity();
        }
    }


    public void print() {
        if (actorMachine != null) {
            PrintDotModule printModule = MultiJ.from(PrintDotModule.class)
                    .bind("actorMachine").to(actorMachine)
                    .bind("name").to(name)
                    .instance();
            printModule.printDot(path);
        }
    }

    @Module
    interface PrintDotModule {

        @Binding(BindingKind.INJECTED)
        ActorMachine actorMachine();

        @Binding(BindingKind.INJECTED)
        String name();

        @Binding(LAZY)
        default Emitter emitter() {
            return new Emitter();
        }

        ;

        void instruction(Instruction instruction, int currentState, int instrCount, Map<State, Integer> stateNumbers);

        default void instruction(Exec exec, int currentState, int instrCount, Map<State, Integer> stateNumbers) {
            emitter().emit(" i%d [shape=rectangle, label=\"%d\"];", instrCount, exec.transition());
            emitter().emit(" %d -> i%d -> %d;", currentState, instrCount, stateNumbers.get(exec.target()));
        }

        default void instruction(Wait wait, int currentState, int instrCount, Map<State, Integer> stateNumbers) {
            emitter().emit(" i%d [shape=doublecircle,label=\"\",width=\"0.2\",heigth=\"0.2\"];", instrCount);
            emitter().emit(" %d -> i%d -> %d;", currentState, instrCount, stateNumbers.get(wait.target()));

        }

        default void instruction(Test test, int currentState, int instrCount, Map<State, Integer> stateNumbers) {
            emitter().emit(" i%d [shape=diamond,label=\"%s\"]", instrCount, condition(actorMachine().getCondition(test.condition())));
            emitter().emit(" %d -> i%d;", currentState, instrCount);
            emitter().emit(" i%d -> %d [style=dashed];", instrCount, stateNumbers.get(test.targetFalse()));
            emitter().emit(" i%d -> %d;", instrCount, stateNumbers.get(test.targetTrue()));
        }

        String condition(Condition condition);

        default String condition(PortCondition condition) {
            if (condition.isInputCondition()) {
                return "tokens(" + condition.getPortName().getName() + ", " + condition.N() + ")";
            } else {
                return "space(" + condition.getPortName().getName() + ", " + condition.N() + ")";
            }
        }

        default String condition(PredicateCondition condition) {
            return "(guard)";
        }

        default Map<State, Integer> stateMap(List<? extends State> stateList) {
            int i = 0;
            Map<State, Integer> result = new HashMap<>();
            for (State s : stateList) {
                result.put(s, i++);
            }
            return result;
        }

        default void printDot(Path path) {
            List<? extends State> stateList = actorMachine().controller().getStateList();
            Map<State, Integer> stateMap = stateMap(stateList);
            int currentState = 0;
            int instrCount = 0;

            emitter().open(path);
            emitter().emit("digraph %s{", name());

            for (State state : actorMachine().controller().getStateList()) {
                emitter().emit(" %d [shape=circle,style=filled];", currentState);
                if (state != null) {
                    for (Instruction inst : state.getInstructions()) {
                        instruction(inst, currentState, instrCount, stateMap);
                        instrCount++;
                    }
                }
                currentState++;
            }

            emitter().emit("}");
            emitter().close();
        }

    }


}
