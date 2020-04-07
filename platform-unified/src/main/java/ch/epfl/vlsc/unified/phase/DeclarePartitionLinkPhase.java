package ch.epfl.vlsc.unified.phase;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.decl.Availability;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;

public class DeclarePartitionLinkPhase implements Phase {
    @Override
    public String getDescription() {
        return "Adds the declaration of PartitionLink ActorMachines to the IR tree";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
//        ActorMachine am =
        GlobalEntityDecl decl = GlobalEntityDecl.global(Availability.PUBLIC, "", null);
        return null;
    }
}
