package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Module
public interface BuildSystem {
    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    /**
     * Create a script that takes the generated MLIR files from the compiler and converts them to SV files.
     * <p>
     * A compile.sh script is created in the project_dir/scripts directory.
     * <p>
     * The sv files are stored in project_dir/build/sv
     */
    default void generateBuildScript() {
        Path mainTarget = PathUtils.getTargetScript(backend().context()).resolve("compile.sh");
        emitter().open(mainTarget);
        emitter().emit("#!/bin/bash");
        emitter().emit("# Script that takes the generated MLIR files from the CAL compiler and runs them through the " +
                "MLIR/CIRCT compiler to generate SV files. The sv files are stored in project_dir/build/sv");
        emitter().emitNewLine();
        emitter().emit("scriptDir=`dirname -- \"$( readlink -f -- \"$0\"; )\";`");
        emitter().emit("cd $scriptDir/..");
        emitter().emit("projDir=`pwd`");
        emitter().emit("echo \"Building project in directory: $projDir\"");
        emitter().emitNewLine();

        emitter().emit("# 1. Build SV files");
        emitter().emit("# 1.1 Build SV files and mlir files for the actor kernels");
        emitter().emit("mkdir -p build/sv");
        emitter().emit("cd build/sv");
        emitter().emit("dfg-opt ../../code-gen/main.mlir --convert-std-to-circt --convert-dfg-to-circt " +
                "--convert-fsm-to-sv --lower-seq-to-sv --export-split-verilog");
        emitter().emitNewLine();

        emitter().emit("# 1.2 Generate the SV files for every actor kernel");
        Set<String> definedInstancesClasses = new HashSet<>(backend().task().getNetwork().getInstances().size());
        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            String entityClass = entityDecl.getOriginalName();
            // Check if this specific instance class has been defined, if not, we define it or else we skip this
            if (definedInstancesClasses.add(entityClass)) {
                String hlsName = "hls_" + entityClass + "_calc";
                emitter().emit("#hlstool %s.mlir --buffering-strategy=cycles --dynamic-hw " +
                        "--lowering-options=disallowLocalVariables -o %s.sv", hlsName, hlsName);
                emitter().emit("hlstool %s.mlir -o %s.sv", hlsName, hlsName);
            }

        }
        emitter().close();
    }

    /**
     * Create a script that generates an OpGraph from the generated MLIR files.
     * <p>
     * A createOpGraph.sh script is created in the project_dir/scripts directory.
     * <p>
     * The graph files are loaded into project_dir/build/graph
     */
    default void generateOpGraphScript() {
        Path mainTarget = PathUtils.getTargetScript(backend().context()).resolve("createOpGraph.sh");

        emitter().open(mainTarget);
        emitter().emit("#!/bin/bash");
        emitter().emit("# A script that generates an OpGraph svg file from the Streamblocks generated main.mlir file." +
                " The svg files is stored in project_dir/build/graph");
        emitter().emit("# Requires the Graphviz software program to be installed on your system.");
        emitter().emitNewLine();
        emitter().emit("scriptDir=`dirname -- \"$( readlink -f -- \"$0\"; )\";`");
        emitter().emit("cd $scriptDir/..");
        emitter().emit("projDir=`pwd`");
        emitter().emit("echo \"Building project in directory: $projDir\"");
        emitter().emitNewLine();

        emitter().emit("# 1. Generate the graph.dot file to be used by Graphviz to generate the software ");
        emitter().emit("mkdir -p build/graph");
        emitter().emit("cd build/graph");
        emitter().emit("dfg-opt ../../code-gen/main.mlir --view-op-graph 2> graph.dot");
        emitter().emitNewLine();

        emitter().emit("# 2. Generate the SVG from the .dot file using Graphviz.");
        emitter().emit("dot -Tsvg graph.dot > graph.svg");
        emitter().emitNewLine();
        emitter().close();
    }
}
