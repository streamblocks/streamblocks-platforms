/*
    StreamBlocks
    Copyright (C) 2018 Endri Bezati

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,s
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package ch.epfl.vlsc.platform.cpp;

import ch.epfl.vlsc.phase.CBackendPhase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;
import se.lth.cs.tycho.platform.Platform;

import java.util.List;

public class C implements Platform {
    @Override
    public String name() {
        return "generic-c";
    }

    @Override
    public String description() {
        return "A backend for C code.";
    }

    private static final List<Phase> phases = ImmutableList.<Phase>builder()
            .addAll(Compiler.frontendPhases())
            .addAll(Compiler.networkElaborationPhases())
            .addAll(Compiler.actorMachinePhases())
            .add(new RemoveUnusedEntityDeclsPhase())
            .add(new CBackendPhase())
            .build();

    @Override
    public List<Phase> phases() {
        return phases;
    }
}
