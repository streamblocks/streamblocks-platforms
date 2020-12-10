package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.compiler.ir.BankedPortDecl;
import ch.epfl.vlsc.hls.backend.systemc.SCInstance;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.stream.Collectors;

@Module
public interface CMakeLists {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void projectCMakeLists() {
        // -- Network
        Network network = backend().task().getNetwork();

        emitter().open(PathUtils.getTarget(backend().context()).resolve("CMakeLists.txt"));

        // -- CMake Minimal version
        emitter().emitSharpBlockCommentStart();
        emitter().emitSharpComment("StremBlocks Vivado HLS Code Generation");
        emitter().emitSharpComment("Generated from: " + backend().task().getIdentifier());
        emitter().emitSharpBlockCommentEnd();

        emitter().emit("cmake_minimum_required(VERSION 3.3)");
        emitter().emitNewLine();

        // -- Project name
        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emitSharpBlockComment("CMake Project");
        emitter().emit("project (%s-vivadohls)", identifier);
        emitter().emitNewLine();
        emitter().emit("set(__NETWORK_TOP_NAME__ %s)", identifier);

        emitter().emitSharpComment("set the instance actors");
        emitter().emit("set(__ACTORS_IN_NETWORK__");
        {
            emitter().increaseIndentation();
            network.getInstances().forEach(inst -> emitter().emit(inst.getInstanceName()));
            emitter().decreaseIndentation();
        }
        emitter().emit(")");

        emitter().emitNewLine();

        emitter().emitSharpComment("set the input/output stage actors");

        emitter().emit("set(__INPUT_STAGE_ACTORS__");
        {
            emitter().increaseIndentation();
            network.getInputPorts().forEach(
                    input -> emitter().emit("%s_input_stage_mem", input.getName()));
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emitNewLine();
        emitter().emit("set(__OUTPUT_STAGE_ACTORS__");
        {
            emitter().increaseIndentation();
            network.getOutputPorts().forEach(
                    output -> emitter().emit("%s_output_stage_mem", output.getName())
            );
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emitNewLine();

        emitter().emitSharpComment("Set the bank name");
        emitter().emit("set(BANK \"bank\")");

        emitter().emit("if(IS_MPSOC)");
        emitter().emit("\tset(BANK \"HP\")");
        emitter().emit("endif()");
        emitter().emitNewLine();

        emitter().emitSharpBlockComment("memory bank configurations");
        emitter().emit("set(__MEMORY_BANK_CONFIGS__");
        {
            emitter().increaseIndentation();
            int banks = 0;
            for (PortDecl port : ImmutableList.concat(network.getInputPorts(), network.getOutputPorts())) {
                int bankId = 0;
                if (port instanceof BankedPortDecl)
                    bankId = ((BankedPortDecl) port).getBankId();
                else {
                    bankId = banks;
                    banks = (banks + 1) % 4;
                }

                emitter().emit("--sp ${__NETWORK_TOP_NAME__}_kernel_1.m_axi_%s:${BANK}%d", port.getName(), bankId);
            }
            emitter().decreaseIndentation();
            emitter().emit(")");
        }

        emitter().emitSharpComment("all the actors in the kernel");

        emitter().emit("set(__ACTORS_IN_KERNEL__");
        {
            emitter().increaseIndentation();
            emitter().emit("${__ACTORS_IN_NETWORK__}");
            emitter().emit("${__INPUT_STAGE_ACTORS__}");
            emitter().emit("${__OUTPUT_STAGE_ACTORS__}");
            emitter().decreaseIndentation();
        }
        emitter().emit(")");

        emitter().emitNewLine();

        emitter().emitSharpComment("Configure the cmake script");
        emitter().emit("configure_file(${PROJECT_SOURCE_DIR}/scripts/Helper.cmake.in " +
                "${PROJECT_SOURCE_DIR}/cmake/Helper.cmake @ONLY)");

        emitter().emitSharpComment("Include  the cmake scripts for generating verilog and xclbin");
        emitter().emit("include(${PROJECT_SOURCE_DIR}/cmake/Helper.cmake)");

        if (backend().context().getConfiguration().get(PlatformSettings.enableSystemC)) {
            emitter().emitSharpComment("Get the systemc simulator files");
            emitter().emit("add_subdirectory(systemc)");
        }

        emitter().close();
    }
}
