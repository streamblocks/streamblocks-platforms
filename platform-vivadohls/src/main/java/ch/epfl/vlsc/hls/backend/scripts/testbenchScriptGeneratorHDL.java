package ch.epfl.vlsc.hls.backend.scripts;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

@Module
public interface testbenchScriptGeneratorHDL {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    /**
     * The HLS backend generates a number of HDL testbenches that can be used when the different HLS actors have been
     * compiled to HLS. These files are spread all over the generated project directory. This function generates a
     * script that builds the required HDL actors from the corresponding HLS descriptions and copies this generated
     * HDL and the testbenches to a single directory in the project.
     */
    default void generateSimpleHDLTestbenchScript() {
        emitter().open(PathUtils.getTargetScript(backend().context()).resolve("generateSimpleHDLTestbenches.sh"));


        emitter().emit("#!/bin/bash");
        emitter().emit("# A very simple script that generates the HDL for every HLS actor and them moves all the");
        emitter().emit("# required files for testing those actors to my_project/verilog_testbench_simulation for");
        emitter().emit("# easy simulation.");
        emitter().emitNewLine();

        emitter().emit("# 1. Make sure we are in the correct directory and print useful info to user ");
        emitter().emit("scriptDir=`dirname -- \"$( readlink -f -- \"$0\"; )\";`");
        emitter().emit("cd $scriptDir/..");
        emitter().emit("projDir=`pwd`");
        emitter().emit("echo \"Project directory: $projDir\"");
        emitter().emit("echo \"Project build directory: $projDir\"build");
        emitter().emit("echo \"HDL testbench files to be stored in directory: $projDir\"verilog_testbench_simulation");
        emitter().emitNewLine();

        emitter().emit("# 2. Run cmake build to generate required makefiles and source code");
        emitter().emit("# echo \"Run cmake build to generate required makefiles and source code\"");
        emitter().emit("mkdir -p build");
        emitter().emit("cd build");
        emitter().emit("cmake .. -DTARGET=hw_emu -DHLS_CLOCK_PERIOD=3.3 -DFPGA_NAME=xcu200-fsgd2104-2-e " +
                "-DPLATFORM=xilinx_u200_xdma_201830_2 -DUSE_VITIS=on -DCMAKE_BUILD_TYPE=Debug");
        emitter().emit("cd ..");
        emitter().emitNewLine();

        emitter().emit("# 3. Begin generating relevant HDL files and copy them to simulation directory");
        emitter().emit("mkdir -p verilog_testbench_simulation");
        emitter().emit("cp code-gen/rtl/fifo.v verilog_testbench_simulation/");
        emitter().emit("cp code-gen/rtl/trigger_common.sv verilog_testbench_simulation/");
        emitter().emit("cp code-gen/rtl/trigger.sv verilog_testbench_simulation/");
        emitter().emitNewLine();

        String identifier = backend().task().getIdentifier().getLast().toString();
        copyTopNetworkFile(identifier);
        Network network = backend().task().getNetwork();
        network.getInstances().forEach(this::copyInstanceFile);

        emitter().emit("echo \"Simulation sources in: $projDir\"");

        emitter().close();
    }

    default void copyTopNetworkFile(String networkName) {
        emitter().emit("echo \"Copy HDL for instance: %s\"", networkName);
        emitter().emit("cp code-gen/rtl/%s.sv verilog_testbench_simulation/", networkName);
        emitter().emit("cp code-gen/rtl-tb/tb_%s.v verilog_testbench_simulation/", networkName);
        emitter().emitNewLine();
    }

    default void copyInstanceFile(Instance instance) {
        String instanceName = instance.getInstanceName();
        emitter().emit("echo \"Generate and copy HDL for instance: %s\"", instanceName);
        emitter().emit("cd build");
        emitter().emit("make %s", instanceName);
        emitter().emit("cp %s/solution/syn/verilog/%s.v ../verilog_testbench_simulation/", instanceName, instanceName);
        emitter().emit("cp ../code-gen/rtl-tb/tb_%s.v ../verilog_testbench_simulation/", instanceName);
        emitter().emit("cd ..");
        emitter().emitNewLine();
    }
}
