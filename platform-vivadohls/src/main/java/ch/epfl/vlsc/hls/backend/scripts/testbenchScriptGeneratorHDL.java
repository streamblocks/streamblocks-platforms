package ch.epfl.vlsc.hls.backend.scripts;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.util.List;
import java.util.stream.Collectors;

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
        emitter().emit("echo \"Project build directory: $projDir/build\"");
        emitter().emit("echo \"HDL testbench files to be stored in directory: $projDir/verilog_testbench_simulation\"");
        emitter().emitNewLine();

        emitter().emit("# 2. Run cmake build to generate required makefiles and source code");
        emitter().emit("# echo \"Run cmake build to generate required makefiles and source code\"");
        emitter().emit("mkdir -p build");
        emitter().emit("cd build");
        emitter().emit("cmake .. -DTARGET=hw_emu -DHLS_CLOCK_PERIOD=3.3 -DFPGA_NAME=xc7z020clg484-1 " +
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
        makeInParallel(network);
        network.getInstances().forEach(this::copyInstanceFile);

        emitter().emit("echo \"Simulation sources in: $projDir/verilog_testbench_simulation\"");

        emitter().close();
    }

    default void makeInParallel(Network network){
        emitter().emit("# An optimisation to generate HDL for all the actors in parallel: Begin");
        emitter().emit("# This is the most time consuming part of this script, so parallel execution speeds things up.");
        emitter().emit("# If this region causes errors in building, just remove it.");
        emitter().emit("cd build");
        emitter().emit("sed -i 's/.NOTPARALLEL/#.NOTPARALLEL/g' Makefile");
        List<String> instanceNamesList = network.getInstances().stream().map(x -> x.getInstanceName()).collect(Collectors.toList());
        emitter().emit("make -j4 %s", String.join(" ", instanceNamesList));
        emitter().emit("sed -i 's/#.NOTPARALLEL/.NOTPARALLEL/g' Makefile");
        emitter().emit("cd ..");
        emitter().emit("# An optimisation to generate HDL for all the actors in parallel: End");
        emitter().emitNewLine();
    }

    default void copyTopNetworkFile(String networkName) {
        emitter().emit("echo \"Copy HDL for instance: %s\"", networkName);
        emitter().emit("cp code-gen/rtl/%s.sv verilog_testbench_simulation/", networkName);
        emitter().emit("cp code-gen/rtl-tb/tb_%s.v verilog_testbench_simulation/", networkName);
        emitter().emit("cp code-gen/rtl-tb/tb_%s_simple.v verilog_testbench_simulation/", networkName);
        emitter().emitNewLine();
    }

    default void copyTopNetworkFileVivado2023(String networkName) {
        emitter().emit("echo \"Copy HDL for instance: %s\"", networkName);
        emitter().emit("cp code-gen/rtl/%s_vivado2023.sv $outputDir/", networkName);
        emitter().emit("cp code-gen/rtl-tb/tb_%s_simple_vivado2023.v $outputDir/", networkName);
        emitter().emitNewLine();
    }

    default void copyInstanceFile(Instance instance) {
        String instanceName = instance.getInstanceName();
        emitter().emit("echo \"Generate and copy HDL for instance: %s\"", instanceName);
        emitter().emit("cd build");
        emitter().emit("make %s", instanceName);
        emitter().emit("cp %s/solution/syn/verilog/*.v ../verilog_testbench_simulation/", instanceName, instanceName);
        emitter().emit("cp ../code-gen/rtl-tb/tb_%s.v ../verilog_testbench_simulation/", instanceName);
        emitter().emit("cp ../code-gen/rtl-tb/tb_%s_simple.v ../verilog_testbench_simulation/", instanceName);
        emitter().emit("cd ..");
        emitter().emitNewLine();
    }

    /**
     * The HLS backend generates a number of HDL testbenches that can be used when the different HLS actors have been
     * compiled to HLS. These files are spread all over the generated project directory. This function generates a
     * script that builds the required HDL actors from the corresponding HLS descriptions and copies this generated
     * HDL and the testbenches to a single directory in the project.
     */
    default void generateSimpleHDLTestbenchScript_Vivado2023() {
        emitter().open(PathUtils.getTargetScript(backend().context()).resolve("generateSimpleHDLTestbenches_vivado2023.sh"));


        emitter().emit("#!/bin/bash");
        emitter().emit("# A very simple script that generates the HDL for every HLS actor and them moves all the");
        emitter().emit("# required files for testing those actors to my_project/verilog_testbench_simulation_vivado_2023 (default) for");
        emitter().emit("# easy simulation. This script specifically generates HDL for Vivado 2023 and also skips.");
        emitter().emit("# CMAKE generation as the CMAKE compilation flow is designed to work with Vivado 2019.");
        emitter().emitNewLine();

        emitter().emit("# 0. Process Parameters");
        emitter().emit("fpga=\"xc7z020clg484-1\"");
        emitter().emit("clock_period_ns=\"5.0\"");
        emitter().emit("outputDir=\"verilog_testbench_simulation_vivado_2023\"");
        emitter().emit("NUM_THREADS=$((`nproc`-4)) # Change this if you want a different number of parallel threads running in this script.");
        emitter().emit("CURRENT_THREADS=0 # Helper variable - do not change this.");
        emitter().emit("HELP=0");
        emitter().emitNewLine();

        emitter().emit("while getopts f:c:o:n:h flag");
        emitter().emit("do");
        emitter().increaseIndentation();
        emitter().emit("case \"${flag}\" in");
        emitter().increaseIndentation();
        emitter().emit("f) fpga=${OPTARG};;");
        emitter().emit("c) clock_period_ns=${OPTARG};;");
        emitter().emit("o) outputDir=${OPTARG};;");
        emitter().emit("n) NUM_THREADS=${OPTARG};;");
        emitter().emit("h) HELP=1;;");
        emitter().decreaseIndentation();
        emitter().emit("esac");
        emitter().decreaseIndentation();
        emitter().emit("done");
        emitter().emitNewLine();

        emitter().emit("if [ $HELP -eq 1 ]");
        emitter().emit("then");
        emitter().increaseIndentation();
        emitter().emit("echo \"Script to generate HDL from HLS from every actor in the project. Takes the following arguments:\"");
        emitter().emit("echo \"    -h Print this message and then exit\"");
        emitter().emit("echo \"    -o OUTPUT_DIRECTORY The directory to store all the generated HDL files (default: " +
                "verilog_testbench_simulation_vivado_2023).\"");
        emitter().emit("echo \"    -f FPGA The fpga to synthesize for (default: xczu7ev-ffvf1517-1-i).\"");
        emitter().emit("echo \"    -c CLOCK_PERIOD_NS The clock period that the HLS must be specified as in nanoseconds (default: 10.0).\"");
        emitter().emit("echo \"    -n NUMBER_OF_THREADS The number of parallel threads to run when running this script (default: Total system processors - 4).\"");
        emitter().emit("exit 1");
        emitter().decreaseIndentation();
        emitter().emit("fi");
        emitter().emitNewLine();

        emitter().emit("# 1. Make sure we are in the correct directory and print useful info to user ");
        emitter().emit("scriptDir=`dirname -- \"$( readlink -f -- \"$0\"; )\";`");
        emitter().emit("cd $scriptDir/..");
        emitter().emit("projDir=`pwd`");
        emitter().emit("echo \"Project directory: $projDir\"");
        emitter().emit("echo \"Project build directory: $projDir/build\"");
        emitter().emit("echo \"HDL testbench files to be stored in directory: $projDir/$outputDir\"");
        emitter().emitNewLine();

        emitter().emit("# 2. Generate Vivado TCL script for generating HDL from all HLS files");
        emitter().emit("mkdir -p build");
        emitter().emit("cd build");
        emitter().emit("cp ../scripts/Synthesis_vitis.tcl.in Synthesis_vivado2023.tcl");
        emitter().emit("sed -i -e \"s@\\${FPGA_NAME}@$fpga@g\" Synthesis_vivado2023.tcl");
        emitter().emit("sed -i -e \"s@\\${HLS_CLOCK_PERIOD}@$clock_period_ns@g\" Synthesis_vivado2023.tcl");
        emitter().emit("sed -i -e \"s@\\${PROJECT_SOURCE_DIR}@$projDir@g\" Synthesis_vivado2023.tcl");
        emitter().emit("sed -i -e 's/open_project $instance_name/open_project ${instance_name}/g' Synthesis_vivado2023.tcl");
        emitter().emit("");


        emitter().emit("cd ..");
        emitter().emitNewLine();

        emitter().emit("# 3. Begin generating relevant HDL files and copy them to simulation directory");
        emitter().emit("mkdir -p $outputDir");
        emitter().emit("cp code-gen/rtl/fifo.v $outputDir/");
        emitter().emit("cp code-gen/rtl/trigger_common.sv $outputDir/");
        emitter().emit("cp code-gen/rtl/trigger.sv $outputDir/");
        emitter().emitNewLine();


        String identifier = backend().task().getIdentifier().getLast().toString();
        copyTopNetworkFileVivado2023(identifier);
        emitter().emit("echo \"Generating HDL for individual actors in parallel\"");
        Network network = backend().task().getNetwork();
        for (int i = 0; i < network.getInstances().size(); i++) {
            makeAndCopyVivado2023(network.getInstances().get(i), i, network.getInstances().size());
        }
        emitter().emit("wait");

        emitter().emit("echo \"Simulation sources in: $projDir/$outputDir\"");

        emitter().close();
    }

    default void makeAndCopyVivado2023(Instance instance, int instanceIndex, int numInstances) {
        String instanceName = instance.getInstanceName();
        emitter().emit("(");
        emitter().increaseIndentation();
        String timestampGeneratingCommand = "$(date -d@$SECONDS -u +%H:%M:%S)";
        emitter().emit("echo \"    %s Starting process %d of %d\"", timestampGeneratingCommand, instanceIndex+1, numInstances);
        emitter().emit("echo \"    Generating and copying HDL for instance: %s. Follow progress in %s_vivado2023.log\"", instanceName, instanceName);
        emitter().emit("cd build");
        emitter().emit("vitis_hls -f Synthesis_vivado2023.tcl -tclargs %s %s.cpp > %s_vivado2023.log", instanceName, instanceName,instanceName);
        emitter().emit("if [ \"$?\" -ne \"0\" ]; then");
        emitter().increaseIndentation();
        emitter().emit("echo \"    Synthesis failed for %s: Extract from log file:\"", instanceName);
        emitter().emit("tail -20 %s_vivado2023.log", instanceName);
        emitter().emit("exit 1");
        emitter().decreaseIndentation();
        emitter().emit("fi");
        emitter().emit("cp %s/solution/syn/verilog/*.v ../$outputDir/", instanceName, instanceName);
        emitter().emit("cp ../code-gen/rtl-tb/tb_%s_simple_vivado2023.v ../$outputDir/", instanceName);
        emitter().emit("cd ..");
        emitter().emit("echo \"    HDL generation for %s complete.\"", instanceName);
        emitter().decreaseIndentation();
        emitter().emit(")&");
        emitter().emitNewLine();
        emitter().emit("if [ \"$(($NUM_THREADS))\" -eq \"$(($CURRENT_THREADS))\" ]");
        emitter().emit("then");
        emitter().increaseIndentation();
        emitter().emit("wait -n # Wait for one of the above processes to finish");
        emitter().decreaseIndentation();
        emitter().emit("else");
        emitter().increaseIndentation();
        emitter().emit("CURRENT_THREADS=$(($CURRENT_THREADS+1))");
        emitter().decreaseIndentation();
        emitter().emit("fi");
        emitter().emitNewLine();
    }

    /**
     * The script generated here is meant to be called after generateSimpleHDLTestbenchScript_Vivado2023.sh. It takes
     * all the HDL build from generateSimpleHDLTestbenchScript_Vivado2023.sh, creates a Vivado project from it and
     * performs synthesis. This is a weird place for this script. At the time of writing this comment, the CMAKE
     * build for vivado 2023 output from streamblocks was not working properly. The CMAKE build should actually be
     * fixed and this script folded into that.
     */
    default void generateVivadoProjectAndSynthesize_Vivado2023() {
        emitter().open(PathUtils.getTargetScript(backend().context()).resolve("generateVivadoProjectAndSynthesize_vivado2023.sh"));


        emitter().emit("#!/bin/bash");
        emitter().emit("# A very simple script that generates the Vivado project for the top level CAL actor in this project.");
        emitter().emit("# This script requires that the HDL for all the sub-actors in the project is generated. As such, it");
        emitter().emit("# is required that you run the script generateSimpleHDLTestbenchScript_Vivado2023.sh first to work.");
        emitter().emitNewLine();

        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emit("# 0. Process Parameters");
        emitter().emit("topName=%s", identifier);
        emitter().emit("fpga=\"xc7z020clg484-1\"");
        emitter().emit("clock_period_ns=\"10.0\"");
        emitter().emit("vivadoProjSubDir=\"vivado_2023_project_dir\"");
        emitter().emit("HELP=0");
        emitter().emitNewLine();

        emitter().emit("while getopts f:c:o:h flag");
        emitter().emit("do");
        emitter().increaseIndentation();
        emitter().emit("case \"${flag}\" in");
        emitter().increaseIndentation();
        emitter().emit("f) fpga=${OPTARG};;");
        emitter().emit("c) clock_period_ns=${OPTARG};;");
        emitter().emit("o) vivadoProjSubDir=${OPTARG};;");
        emitter().emit("h) HELP=1;;");
        emitter().decreaseIndentation();
        emitter().emit("esac");
        emitter().decreaseIndentation();
        emitter().emit("done");
        emitter().emitNewLine();

        emitter().emit("if [ $HELP -eq 1 ]");
        emitter().emit("then");
        emitter().increaseIndentation();
        emitter().emit("echo \"Script to generate an HDL project for the $topName actor. Must be run after generateSimpleHDLTestbenchScript_Vivado2023.sh is run to work:\"");
        emitter().emit("echo \"    -h Print this message and then exit\"");
        emitter().emit("echo \"    -o OUTPUT_DIRECTORY The directory to store project (default: " +
                "vivado_2023_project_dir).\"");
        emitter().emit("echo \"    -f FPGA The fpga to synthesize for (default: xczu7ev-ffvf1517-1-i).\"");
        emitter().emit("echo \"    -c CLOCK_PERIOD_NS The clock period that the HLS must be specified as in nanoseconds (default: 10.0).\"");
        emitter().emit("exit 1");
        emitter().decreaseIndentation();
        emitter().emit("fi");
        emitter().emitNewLine();

        emitter().emit("# 1. Make sure we are in the correct directory and print useful info to user ");
        emitter().emit("scriptDir=`dirname -- \"$( readlink -f -- \"$0\"; )\";`");
        emitter().emit("cd $scriptDir/..");
        emitter().emit("projDir=`pwd`");
        emitter().emit("echo \"Project directory: $projDir\"");
        emitter().emit("echo \"Project build directory: $projDir/build\"");
        emitter().emit("echo \"Vivado Project to be stored in directory: $projDir/$vivadoProjSubDir\"");
        emitter().emit("cd build");
        emitter().emitNewLine();


        emitter().emit("# 2. Generate the TCL script to create the Vivado project by modifying the template script provided. ");
        emitter().emit("cp ../scripts/$topName.tcl.in $topName.tcl");
        emitter().emit("sed -i \"0,/@PROJECT_SOURCE_DIR@/{s#@PROJECT_SOURCE_DIR@/output#$projDir/$vivadoProjSubDir#}\" $topName.tcl");
        emitter().emit("sed -i -e \"s#@PROJECT_SOURCE_DIR@#$projDir#g\" $topName.tcl");
        emitter().emit("sed -i -e \"s#@CMAKE_CURRENT_BINARY_DIR@#$projDir/build#g\" $topName.tcl");
        emitter().emit("sed -i -e \"s#@FPGA_NAME@#$fpga#g\" $topName.tcl");
        emitter().emit("sed -i -e \"s#${topName}.sv#${topName}_vivado2023.sv#g\" $topName.tcl");
        emitter().emit("sed -i -e \"s#${topName}_pure.sv#${topName}_pure_vivado2023.sv#g\" $topName.tcl");
        emitter().emit("echo \"exit\" >> $topName.tcl");
        emitter().emitNewLine();

        emitter().emit("# 3. Generate clock constraint file from template");
        emitter().emit("cp ../scripts/clock.xdc.in ../code-gen/xdc/clock.xdc");
        emitter().emit("sed -i -e \"s#\\${HLS_CLOCK_PERIOD}#$clock_period_ns#g\"  ../code-gen/xdc/clock.xdc");
        emitter().emitNewLine();

        emitter().emit("# 4. Generate TCL script that adds some extra testbenches to the project");
        emitter().emit("# Also generate a more comprehensive utilisation report");
        emitter().emit("echo \"");
        emitter().emit("open_project $projDir/$vivadoProjSubDir/$topName/$topName.xpr");
        emitter().emit("import_files -fileset sim_1 -norecurse {$projDir/code-gen/rtl-tb/tb_$topName.v}");
        emitter().emit("import_files -fileset sim_1 -norecurse {$projDir/code-gen/rtl-tb/tb_${topName}_simple_vivado2023.v}");
        emitter().emit("set_property top tb_${topName}_simple_vivado2023 [get_filesets sim_1]");
        emitter().emit("set_property top_lib xil_defaultlib [get_filesets sim_1]");
        emitter().emit("open_run synth_1 -name synth_1");
        emitter().emit("report_utilization -file report_utilization.rpt -hierarchical");
        emitter().emit("close_design");
        emitter().emit("exit");
        emitter().emit("\" > addTestBenchToProject.tcl");
        emitter().emitNewLine();

        emitter().emit("# 5. Run the generated Vivado Scripts");
        emitter().emit("vivado -script $topName.tcl -mode tcl");
        emitter().emit("vivado -script addTestBenchToProject.tcl -mode tcl");
        emitter().emitNewLine();

        emitter().emit("echo \"Vivado Project in: $projDir/$vivadoProjSubDir\"");

        emitter().close();
    }

}
