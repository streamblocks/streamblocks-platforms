package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;

import java.util.Optional;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;

@Module
public interface PackageKernels {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getPackageKernels() {
        getPackageKernel("core");
        getPackageKernel("input");
        getPackageKernel("output");
    }

    default String getPackageName(String kernelType) {
        return "package_" + backend().kernel().getKernelName(kernelType);
    }
    default void getPackageKernel(String kernelType) {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Package name
        String packageName = getPackageName(kernelType);

        // -- Kernel name

        // -- Network file
        emitter().open(PathUtils.getTargetScript(backend().context()).resolve(packageName + ".tcl.in"));

        // -- Paths
        emitter().emit("set path_to_packaged \"@CMAKE_SOURCE_DIR@/output/"
                + backend().kernel().getKernelName(kernelType) + "/packaged_kernel_${suffix}\"");
        emitter().emit("set path_to_tmp_project \"@CMAKE_SOURCE_DIR@/output/"
                + backend().kernel().getKernelName(kernelType) + "/tmp_kernel_pack_${suffix}\"");
        emitter().emitNewLine();

        // -- Create Project
        emitter().emit("create_project -force kernel_pack $path_to_tmp_project ");

        // -- Import common files
        backend().vivadotcl().importStreamblocksCommonVerilogFiles();
        // -- Import kernel specific files
        importKernelVerilogFiles(network, identifier, kernelType);

        // -- Package project
        emitter().emit(
                "ipx::package_project -root_dir $path_to_packaged -vendor epfl.ch -library RTLKernel -taxonomy /KernelIP -import_files -set_current false");

        // -- Unload core
        emitter().emit("ipx::unload_core $path_to_packaged/component.xml");

        // -- Edit in project
        emitter().emit(
                "ipx::edit_ip_in_project -upgrade true -name tmp_edit_project -directory $path_to_packaged $path_to_packaged/component.xml");

        // -- Code revision
        emitter().emit("set_property core_revision 1 [ipx::current_core]");

        // -- Remove user parameters
        emitter().emit("foreach up [ipx::get_user_parameters] {");
        emitter().emit("\tipx::remove_user_parameter [get_property NAME $up] [ipx::current_core]");
        emitter().emit("}");
        emitter().emitNewLine();

        emitter().emit("set_property sdx_kernel true [ipx::current_core]");
        emitter().emit("set_property sdx_kernel_type rtl [ipx::current_core]");
        emitter().emit("ipx::create_xgui_files [ipx::current_core]");
        emitter().emitNewLine();

        // -- Kernel IO
        if (kernelType == "input") {
            for (PortDecl port : network.getInputPorts()) {
                emitter().emit("ipx::associate_bus_interfaces -busif m_axi_%s -clock ap_clk [ipx::current_core]",
                        port.getName());
            }
            for (PortDecl port: network.getInputPorts()) {
                emitter().emit("ipx::associate_bus_interfaces -busif %s -clock ap_clk [ipx::current_core]",
                    backend().kernel().getPipeName(port));
            }
        }
            
        else if (kernelType == "output") {
            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit("ipx::associate_bus_interfaces -busif m_axi_%s -clock ap_clk [ipx::current_core]",
                        port.getName());
            }
            for (PortDecl port: network.getOutputPorts()) {
                emitter().emit("ipx::associate_bus_interfaces -busif %s -clock ap_clk [ipx::current_core]",
                    backend().kernel().getPipeName(port));
            }
        }

        else if (kernelType == "core") {
            for (PortDecl port: network.getInputPorts()) {
                emitter().emit("ipx::associate_bus_interfaces -busif %s -clock ap_clk [ipx::current_core]",
                    backend().kernel().getPipeName(port));
            }
            for (PortDecl port: network.getOutputPorts()) {
                emitter().emit("ipx::associate_bus_interfaces -busif %s -clock ap_clk [ipx::current_core]",
                    backend().kernel().getPipeName(port));
            }
        }
            

        emitter().emit("ipx::associate_bus_interfaces -busif s_axi_control -clock ap_clk [ipx::current_core]");
        emitter().emitNewLine();

        emitter().emit("set_property xpm_libraries {XPM_CDC XPM_MEMORY XPM_FIFO} [ipx::current_core]");
        emitter().emit("set_property supported_families { } [ipx::current_core]");
        emitter().emit("set_property auto_family_support_level level_2 [ipx::current_core]");
        emitter().emit("ipx::update_checksums [ipx::current_core]");
        emitter().emit("ipx::save_core [ipx::current_core]");
        emitter().emitNewLine();

        emitter().emit("close_project -delete");

        emitter().close();
    }

    // default void importKernelVerilogFiles(Network network, String identifier) {
    // emitter().emitSharpBlockComment("Import StreamBlocks Kernel Verilog RTL
    // files");
    // for (PortDecl port : network.getInputPorts()) {
    // emitter().emit("import_files -norecurse
    // {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_input_stage.sv}",
    // port.getName());
    // }
    // for (PortDecl port : network.getOutputPorts()) {
    // emitter().emit("import_files -norecurse
    // {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_output_stage.sv}",
    // port.getName());
    // }
    // emitter().emit("import_files -norecurse
    // {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_control_s_axi.v}", identifier);
    // emitter().emit("import_files -norecurse
    // {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_wrapper.sv}", identifier);
    // emitter().emit("import_files -norecurse
    // {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_kernel.v}", identifier);
    // emitter().emitNewLine();
    // }

    default void importKernelVerilogFiles(Network network, String identifier, String kernelType) {
        emitter().emitSharpBlockComment("Import StreamBlocks " + kernelType + " kernel Verilog RTL files");
        Optional<ImmutableList<PortDecl>> kernelIO = Optional.empty();
        if (kernelType == "input") {
            kernelIO = Optional.of(network.getInputPorts());
        } else if (kernelType == "output") {
            kernelIO = Optional.of(network.getOutputPorts());
        }
        String kernelPostfix = kernelType + "_kernel";
        String kernelName = backend().kernel().getKernelName(kernelType);

        importKernelTopVerilogFiles(kernelPostfix, kernelName);

        if (kernelIO.isPresent()) {
            for (PortDecl port : kernelIO.get()) {
                emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s/%s_%s_stage.sv}",
                        kernelPostfix, port.getName(), kernelType);

            }
            emitter().emitSharpBlockComment("Import I/O stages");
            for (PortDecl port : kernelIO.get()) {
                importVivadoHLSIOStage(port, kernelType + "_stage_mem");
                importVivadoHLSIOStage(port, "stage_pass");
            }
        } else {
            // import verilog network files
            emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s/%s.sv}", kernelPostfix,
                    identifier);
            backend().vivadotcl().importVivadoHLSVerilogFiles(network);
        }

    }

    default void importKernelTopVerilogFiles(String kernelPostfix, String kernelName) {
        emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s/%s_control_s_axi.v}", kernelPostfix,
                kernelName);
        emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s/%s_wrapper.sv}", kernelPostfix,
                kernelName);
        emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s/%s.v}", kernelPostfix, kernelName);
    }

    default void importVivadoHLSIOStage(PortDecl port, String name) {
        String ioStageId = port.getName() + "_" + name;
        emitter().emit("# -- Import files for %s", ioStageId);
        emitter().emit("set %s_files [glob -directory @CMAKE_CURRENT_BINARY_DIR@/%1$s/solution/syn/verilog *{v,dat}]",
                ioStageId);
        emitter().emit("import_files -norecurse $%s_files", ioStageId);
        emitter().emitNewLine();
    }

}
