package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;

@Module
public interface PackageKernel {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getPackageKernel() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Network file
        emitter().open(PathUtils.getTargetScript(backend().context()).resolve("package_kernel.tcl.in"));

        // -- Paths
        emitter().emit("set path_to_packaged \"@CMAKE_SOURCE_DIR@/output/kernel/packaged_kernel_${suffix}\"");
        emitter().emit("set path_to_tmp_project \"@CMAKE_SOURCE_DIR@/output/kernel/tmp_kernel_pack_${suffix}\"");
        emitter().emitNewLine();

        // -- Create Project
        emitter().emit("create_project -force kernel_pack $path_to_tmp_project ");

        // -- Import Files
        backend().vivadotcl().importVivadoHLSVerilogFiles(network);
        backend().vivadotcl().importStreamblocksVerilogFiles(identifier);
        importKernelVerilogFiles(network, identifier);

        emitter().emitSharpBlockComment("Import Input/Output stages");
        for (PortDecl port : network.getInputPorts()) {
            importVivadoHLSIOStage(port, "_input_stage_mem");
            importVivadoHLSIOStage(port, "_input_stage_pass");
        }
        for (PortDecl port : network.getOutputPorts()) {
            importVivadoHLSIOStage(port, "_output_stage_control");
            importVivadoHLSIOStage(port, "_output_stage_mem");
        }

        // -- Package project
        emitter().emit("ipx::package_project -root_dir $path_to_packaged -vendor epfl.ch -library RTLKernel -taxonomy /KernelIP -import_files -set_current false");

        // -- Unload core
        emitter().emit("ipx::unload_core $path_to_packaged/component.xml");

        // -- Edit in project
        emitter().emit("ipx::edit_ip_in_project -upgrade true -name tmp_edit_project -directory $path_to_packaged $path_to_packaged/component.xml");

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
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("ipx::associate_bus_interfaces -busif m_axi_%s -clock ap_clk [ipx::current_core]", port.getName());
        }

        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("ipx::associate_bus_interfaces -busif m_axi_%s -clock ap_clk [ipx::current_core]", port.getName());
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

    default void importKernelVerilogFiles(Network network, String identifier) {
        emitter().emitSharpBlockComment("Import StreamBlocks Kernel Verilog RTL files");
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_input_stage.v}", port.getName());
        }
        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_output_stage.v}", port.getName());
        }
        emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_control_s_axi.v}", identifier);
        emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_wrapper.sv}", identifier);
        emitter().emit("import_files -norecurse {@CMAKE_SOURCE_DIR@/code-gen/rtl/%s_kernel.v}", identifier);
        emitter().emitNewLine();
    }

    default void importVivadoHLSIOStage(PortDecl port, String name) {
        String ioStageId = port.getName() + "_" + name;
        emitter().emit("# -- Import files for %s", ioStageId);
        emitter().emit("set %s_files [glob -directory @CMAKE_CURRENT_BINARY_DIR@/%1$s/solution/syn/verilog *{v,dat}]", ioStageId);
        emitter().emit("import_files -norecurse $%s_files", ioStageId);
        emitter().emitNewLine();
    }

}
