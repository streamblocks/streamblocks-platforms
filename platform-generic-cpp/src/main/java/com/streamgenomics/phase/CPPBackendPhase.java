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

package com.streamgenomics.phase;

import com.streamgenomics.backend.cpp.Backend;
import com.streamgenomics.backend.cpp.Controllers;
import org.multij.MultiJ;
import platformutils.Utils;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.settings.Setting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class CPPBackendPhase implements Phase {

    /**
     * Code generation path
     */
    private Path codeGenPath;

    /**
     * Code generation source path
     */
    private Path codeGenPathSrc;

    /**
     * Code generation include path
     */
    private Path codeGenPathInclude;

    /**
     * Libraries path
     */
    private Path libPath;

    /**
     * Libraries source path
     */
    private Path libPathSrc;

    /**
     * Libraries include path
     */
    private Path libPathInclude;

    /**
     * Binary path
     */
    private Path binPath;

    /**
     * CMake build path
     */
    private Path buildPath;

    /**
     * Target Path
     */
    private Path targetPath;


    @Override
    public String getDescription() {
        return "StreamBlocks CPP Platform for Tycho compiler";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(Controllers.scopeLivenessAnalysis);
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) {
        // -- Create directories
        createDirectories(context);

        Path path = context.getConfiguration().get(Compiler.targetPath);
        String filename = "prelude.h";
        copyResource(path, filename);
        Backend backend = MultiJ.from(Backend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();
        backend.main().generateCode();
        return task;
    }

    private void copyResource(Path path, String filename) {
        try {
            Files.copy(ClassLoader.getSystemResourceAsStream("c_backend_code/" + filename), path.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not generate code to \"" + filename + "\""));
        }
    }

    private void createDirectories(Context context){
        // -- Get target Path
        targetPath = context.getConfiguration().get(Compiler.targetPath);

        // -- Code Generation paths
        codeGenPath = Utils.createDirectory(targetPath,"code-gen");
        codeGenPathSrc = Utils.createDirectory(codeGenPath, "src");
        codeGenPathInclude = Utils.createDirectory(codeGenPath, "include");

        // -- Library paths
        libPath = Utils.createDirectory(targetPath,"lib");
        libPathSrc = Utils.createDirectory(targetPath, "src");
        libPathInclude = Utils.createDirectory(targetPath, "include");

        // -- Build path
        buildPath = Utils.createDirectory(targetPath,"build");

        // -- Binary path
        binPath = Utils.createDirectory(targetPath, "bin");


    }
}
