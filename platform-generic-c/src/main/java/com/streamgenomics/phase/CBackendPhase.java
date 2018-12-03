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
import platformutils.PathUtils;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Setting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class CBackendPhase implements Phase {

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


    private void copyResource(Path path, String filename) {
        try {
            Files.copy(ClassLoader.getSystemResourceAsStream("c_backend_code/" + filename), path.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not generate code to \"" + filename + "\""));
        }
    }

    private void createDirectories(Context context) {
        // -- Get target Path
        targetPath = context.getConfiguration().get(Compiler.targetPath);

        // -- Code Generation paths
        codeGenPath = PathUtils.createDirectory(targetPath, "code-gen");
        codeGenPathSrc = PathUtils.createDirectory(codeGenPath, "src");
        codeGenPathInclude = PathUtils.createDirectory(codeGenPath, "include");

        // -- Library paths
        //libPath = PathUtils.createDirectory(targetPath, "lib");
        //libPathSrc = PathUtils.createDirectory(libPath, "src");
        //libPathInclude = PathUtils.createDirectory(libPath, "include");

        // -- Build path
        buildPath = PathUtils.createDirectory(targetPath, "build");

        // -- Binary path
        binPath = PathUtils.createDirectory(targetPath, "bin");

    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) {
        // -- Get Reporter
        Reporter reporter = context.getReporter();
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, "StreamBlocks C Code Generation"));
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, "Identifier, " + task.getIdentifier().toString()));
        reporter.report(new Diagnostic(Diagnostic.Kind.INFO, "Target Path, " + PathUtils.getTarget(context)));


        // -- Create directories
        createDirectories(context);

        // -- Instantiate backend, bind current compilation task and the context
        Backend backend = MultiJ.from(Backend.class)
                .bind("task").to(task)
                .bind("context").to(context)
                .instance();

        // -- Generate actors
        generateActors(backend);

        // -- Generate Network
        generateNetwork(backend);

        // -- Generate Globals
        generateGlobal(backend);

        // -- Generate Channels
        generateChannels(backend);

        // -- Generate CMakeLists
        generateCmakeLists(backend);

        // -- Backend resources
        copyBackedResources(backend);
        return task;
    }

    private void generateNetwork(Backend backend) {
        // -- Generate Main
        backend.main().generateMain();
    }

    /**
     * Generates the source code for all actors
     *
     * @param backend
     */
    private void generateActors(Backend backend) {
        for (Instance instance : backend.task().getNetwork().getInstances()) {
            backend.actors().generateActor(instance);
        }
    }

    /**
     * Generates the various CMakeLists.txt for building the generated code
     *
     * @param backend
     */
    private void generateCmakeLists(Backend backend) {
        // -- Top CMakeLists
        backend.cmakelists().generateTopCmakeLists();

        // -- Lib CMakeLists
        // backend.cmakelists().generateLibCmakeLists();

        // -- Code-Gen CMakeLists
        backend.cmakelists().generateCodeGenCmakeLists();
    }

    /**
     * Generates the global source code and header
     *
     * @param backend
     */
    private void generateGlobal(Backend backend) {
        // -- Global source code
        Path srcPath = PathUtils.getTargetCodeGenSource(backend.context());
        backend.global().generateGlobalCode(srcPath.resolve("global.c"));

        // -- Global header code
        Path headerPath = PathUtils.getTargetCodeGenInclude(backend.context());
        backend.global().generateGlobalHeader(headerPath.resolve("global.h"));
    }

    /**
     * Genetates the fifo source code
     *
     * @param backend
     */
    private void generateChannels(Backend backend) {
        // -- Channel header
        Path path = PathUtils.getTargetCodeGenInclude(backend.context());
        backend.channels().generateFifoHeader(path.resolve("fifo.h"));
    }

    /**
     * Copies all the resources source code files to the target folder
     *
     * @param backend
     */
    private void copyBackedResources(Backend backend) {
        String filename = "prelude.h";
        copyResource(codeGenPathInclude, filename);
    }
}
