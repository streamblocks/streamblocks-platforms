package ch.epfl.vlsc.wsim.phase;


import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.transformation.RenameVariables;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CppRenameVariablesPhase implements Phase {
    @Override
    public String getDescription() {
        return "rename variables for C++ code generation";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        TreeShadow tree = task.getModule(TreeShadow.key);

        Function<String, String> escape = name -> name.replace("_", "__");

        Function<VarDecl, String> renameFunc = decl -> {
            IRNode parent = tree.parent(decl);
            if (parent instanceof Scope) {
                return "a_" + escape.apply(decl.getName());
            } else if (parent instanceof ActorMachine) {
                return "a_" + escape.apply(decl.getName());
            } else if (parent instanceof NamespaceDecl) {
                QID ns = ((NamespaceDecl) parent).getQID();
                return Stream.concat(ns.parts().stream(), Stream.of(decl.getName()))
                        .map(escape)
                        .collect(Collectors.joining("_", "g_", ""));
            } else {
                return "l_" + escape.apply(decl.getName());
            }
        };
        CompilationTask renamedTask = (CompilationTask) RenameVariables.rename(task, renameFunc, task);
        return renamedTask;
    }



}
