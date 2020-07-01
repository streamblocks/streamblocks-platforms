package ch.epfl.vlsc.hls.backend.simulators;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;

import java.util.List;

@Module
public interface WcfgWaveform {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getWcfg(Network network) {

        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenWcfg(backend().context()).resolve("tb_" + identifier + "_behav.wcfg"));

        emitter().emit("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        emitter().emit("<wave_config>");
        getState();
        dbRefList(identifier);
        wvObjectSize(2);
        int groupCounter = createGroupTB(identifier, network.getInputPorts(), network.getOutputPorts(), 0);
        createGroupDUT(network, groupCounter);
        emitter().emit("</wave_config>");

        emitter().close();
    }

    default void getState() {
        emitter().emit("<wave_state>");
        emitter().emit("</wave_state>");
    }

    default void dbRefList(String name) {
        emitter().emit("<db_ref_list>");
        {
            emitter().increaseIndentation();

            emitter().emit("<db_ref path=\"%s_tb_func_synth.wdb\" id=\"1\">", name);
            {
                emitter().increaseIndentation();

                emitter().emit("<top_modules>");
                {
                    emitter().increaseIndentation();
                    emitter().emit("<top_module name=\"tb_%s\" />", name);
                    emitter().emit("<top_module name=\"glbl\" />");
                    emitter().decreaseIndentation();
                }
                emitter().emit("</top_modules>");

                emitter().decreaseIndentation();
            }
            emitter().emit(" </db_ref>");

            emitter().decreaseIndentation();
        }
        emitter().emit("</db_ref_list>");
    }

    default void wvObjectSize(int size) {
        emitter().emit("<WVObjectSize size=\"%d\" />", size);
    }

    default void wvObject(String type, String hierarchy, String name) {
        emitter().emit("<wvobject type=\"%s\" fp_name=\"/%s/%s\">", type, hierarchy, name);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"ElementShortName\">%s</obj_property>", name);
            emitter().emit("<obj_property name=\"ObjectShortName\">%s</obj_property>", name);

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
    }

    default void wvObjectArrayPort(String hierarchy, PortDecl port, boolean isInput, boolean hasExtension) {
        String portName = port.getName();
        Type type = backend().types().declaredPortType(port);
        int bitSize = backend().typeseval().sizeOfBits(type);
        emitter().emit("<wvobject type=\"array\" fp_name=\"/%s/%s%s%s\">", hierarchy, portName, getPortExtension(hasExtension), isInput ? "_dout" : "_din");
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"ElementShortName\">%s%s%s[%d:0]</obj_property>", portName, getPortExtension(hasExtension), isInput ? "_dout" : "_din", bitSize - 1);
            emitter().emit("<obj_property name=\"ObjectShortName\">%s%s%s[%d:0]</obj_property>", portName, getPortExtension(hasExtension), isInput ? "_dout" : "_din", bitSize - 1);
            if (type instanceof IntType) {
                if (((IntType) type).isSigned()) {
                    emitter().emit("<obj_property name=\"Radix\">SIGNEDDECRADIX</obj_property>");
                } else {
                    emitter().emit("<obj_property name=\"Radix\">UNSIGNEDDECRADIX</obj_property>");
                }
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
    }
/*
    default void wvObjectArrayPort(String hierarchy, PortDecl port, String nameExtension, boolean isInput, boolean hasExtension) {
        String portName = port.getName();
        Type type = backend().types().declaredPortType(port);
        int bitSize = TypeUtils.sizeOfBits(type);
        emitter().emit("<wvobject type=\"array\" fp_name=\"/%s/%s_%s%s%s\">", hierarchy, portName, nameExtension, getPortExtension(hasExtension), isInput ? "_dout" : "_din");
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"ElementShortName\">%s_%s%s%s[%d:0]</obj_property>", portName, nameExtension, getPortExtension(hasExtension), isInput ? "_dout" : "_din", bitSize - 1);
            emitter().emit("<obj_property name=\"ObjectShortName\">%s_%s%s%s[%d:0]</obj_property>", portName, nameExtension, getPortExtension(hasExtension), isInput ? "_dout" : "_din", bitSize - 1);
            if (type instanceof IntType) {
                if (((IntType) type).isSigned()) {
                    emitter().emit("<obj_property name=\"Radix\">SIGNEDDECRADIX</obj_property>");
                } else {
                    emitter().emit("<obj_property name=\"Radix\">UNSIGNEDDECRADIX</obj_property>");
                }
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
    }
*/

    default void wvObjectArray(String hierarchy, String name, Integer nbBits) {
        emitter().emit("<wvobject type=\"array\" fp_name=\"/%s/%s\">", hierarchy, name);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"ElementShortName\">%s[%d:0]</obj_property>", name, nbBits);
            emitter().emit("<obj_property name=\"ObjectShortName\">%s[%d:0]</obj_property>", name, nbBits);
            emitter().emit("<obj_property name=\"Radix\">UNSIGNEDDECRADIX</obj_property>");

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
    }

    // ------------------------------------------------------------------------
    // -- Testbench

    default int createGroupTB(String name, List<PortDecl> inputs, List<PortDecl> outputs, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">TB</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");

            currentGroupCounter = createGroupCLK(name, currentGroupCounter);
            currentGroupCounter = createGroupRESET(name, currentGroupCounter);
            currentGroupCounter = createGroupCONTROL(name, currentGroupCounter);
            currentGroupCounter = createGroupInputOutputTB(name, inputs, outputs, currentGroupCounter);

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
        return currentGroupCounter;
    }

    default int createGroupCLK(String name, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">CLK</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            wvObject("other", "tb_" + name, "cycle");
            wvObject("logic", "tb_" + name, "clock");

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
        return currentGroupCounter;
    }


    default int createGroupRESET(String name, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">RESET</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            wvObject("logic", "tb_" + name, "reset_n");

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
        return currentGroupCounter;
    }

    default int createGroupCONTROL(String name, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">CONTROL</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            wvObject("logic", "tb_" + name, "start");

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
        return currentGroupCounter;
    }

    default int createGroupInputOutputTB(String name, List<PortDecl> inputs, List<PortDecl> outputs, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">INPUTS</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            for (PortDecl port : inputs) {
                currentGroupCounter = createGroupInputDUT(name, port, currentGroupCounter);
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">OUTPUTS</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");

            for (PortDecl port : outputs) {
                currentGroupCounter = createGroupOutputDUT(name, port, currentGroupCounter);
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");

        return currentGroupCounter;
    }


    default int createGroupInputDUT(String name, PortDecl port, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">%s</obj_property>", port.getName());
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            wvObjectArrayPort("tb_" + name, port, false, false);
            wvObject("logic", "tb_" + name, port.getName() + "_write");
            wvObject("logic", "tb_" + name, port.getName() + "_full_n");

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
        return currentGroupCounter;
    }

    default int createGroupOutputDUT(String name, PortDecl port, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        Type type = backend().types().declaredPortType(port);
        int bitSize = backend().typeseval().sizeOfBits(type);
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">%s</obj_property>", port.getName());
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            wvObjectArray("tb_" + name, port.getName() + "_token_counter", 31);
            wvObjectArray("tb_" + name, port.getName() + "_exp_value", bitSize - 1);


            wvObjectArrayPort("tb_" + name, port, true, false);
            wvObject("logic", "tb_" + name, port.getName() + "_read");
            wvObject("logic", "tb_" + name, port.getName() + "_empty_n");

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
        return currentGroupCounter;
    }

    // ------------------------------------------------------------------------
    // -- Design under test

    default int createGroupDUT(Network network, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">DUT</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            currentGroupCounter = createGroupInstances(network, currentGroupCounter);
            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");


        return currentGroupCounter;
    }

    default int createGroupInstances(Network network, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">Instances</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            for (Instance instance : network.getInstances()) {
                currentGroupCounter = createGroupInstance(instance, currentGroupCounter);
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");

        return currentGroupCounter;
    }

    default int createGroupInstance(Instance instance, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        String identifier = "i_" + instance.getInstanceName();
        String hierarchy = "tb_" + backend().task().getIdentifier().getLast().toString() + "/dut";
        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">%s</obj_property>", identifier);
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            getWvObjectClkReset(hierarchy, identifier);
            currentGroupCounter = getGroupApCtrl(hierarchy, identifier, currentGroupCounter);


            // -- Get Entity
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            Entity entity = entityDecl.getEntity();

            // -- Inputs
            currentGroupCounter++;
            if (!entity.getInputPorts().isEmpty()) {
                emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
                emitter().emit("<obj_property name=\"label\">INPUT%s</obj_property>", entity.getInputPorts().size() > 1 ? "S" : "");
                emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
                for (PortDecl port : entity.getInputPorts()) {
                    currentGroupCounter = createGroupInstanceIO(hierarchy, identifier, port, true, currentGroupCounter);
                }
                emitter().emit("</wvobject>");
            }

            // -- Outputs
            currentGroupCounter++;
            if (!entity.getOutputPorts().isEmpty()) {
                emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
                emitter().emit("<obj_property name=\"label\">OUTPUT%s</obj_property>", entity.getOutputPorts().size() > 1 ? "S" : "");
                emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
                for (PortDecl port : entity.getOutputPorts()) {
                    currentGroupCounter = createGroupInstanceIO(hierarchy, identifier, port, false, currentGroupCounter);
                }
                emitter().emit("</wvobject>");
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");


        return currentGroupCounter;
    }


    default int createGroupInstanceIO(String hierarchy, String name, PortDecl port, boolean isInput, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;
        String portName = port.getName();

        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">%s</obj_property>", portName);
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            wvObjectArrayPort(hierarchy + "/" + name, port, isInput, true);
            if (isInput) {
                wvObject("logic", hierarchy + "/" + name, portName + getPortExtension(true) + "_read");
                wvObject("logic", hierarchy + "/" + name, portName + getPortExtension(true) + "_empty_n");
            } else {
                wvObject("logic", hierarchy + "/" + name, portName + getPortExtension(true) + "_write");
                wvObject("logic", hierarchy + "/" + name, portName + getPortExtension(true) + "_full_n");
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");

        return currentGroupCounter;
    }


    // ------------------------------------------------------------------------
    // -- Helper methods
    default String getPortExtension(boolean isInstance) {
        if (isInstance) {
            return "_V";
        } else {
            return "";
        }
    }

    default void getWvObjectClkReset(String hierarchy, String name) {
        emitter().emit("<wvobject type=\"logic\" fp_name=\"/%s/%s/ap_clk\">", hierarchy, name);
        {
            emitter().increaseIndentation();
            emitter().emit("<obj_property name=\"ElementShortName\">ap_clk</obj_property>");
            emitter().emit("<obj_property name=\"ObjectShortName\">ap_clk</obj_property>");
            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
        emitter().emit("<wvobject type=\"logic\" fp_name=\"/%s/%s/ap_rst_n\">", hierarchy, name);
        {
            emitter().increaseIndentation();
            emitter().emit("<obj_property name=\"ElementShortName\">ap_rst_n</obj_property>");
            emitter().emit("<obj_property name=\"ObjectShortName\">ap_rst_n</obj_property>");
            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");
    }

    default int getGroupApCtrl(String hierarchy, String name, int groupCounter) {
        int currentGroupCounter = groupCounter;
        currentGroupCounter++;

        emitter().emit("<wvobject type=\"group\" fp_name=\"%d\">", currentGroupCounter);
        {
            emitter().increaseIndentation();

            emitter().emit("<obj_property name=\"label\">ap_ctrl</obj_property>");
            emitter().emit("<obj_property name=\"DisplayName\">label</obj_property>");
            emitter().emit("<obj_property name=\"isExpanded\"></obj_property>");

            // -- AP start
            emitter().emit("<wvobject type=\"logic\" fp_name=\"/%s/%s/ap_start\">", hierarchy, name);
            {
                emitter().increaseIndentation();

                emitter().emit("<obj_property name=\"ElementShortName\">ap_start</obj_property>");
                emitter().emit("<obj_property name=\"ObjectShortName\">ap_start</obj_property>");

                emitter().decreaseIndentation();
            }
            emitter().emit("</wvobject>");

            // -- AP done
            emitter().emit("<wvobject type=\"logic\" fp_name=\"/%s/%s/ap_done\">", hierarchy, name);
            {
                emitter().increaseIndentation();

                emitter().emit("<obj_property name=\"ElementShortName\">ap_done</obj_property>");
                emitter().emit("<obj_property name=\"ObjectShortName\">ap_done</obj_property>");

                emitter().decreaseIndentation();
            }
            emitter().emit("</wvobject>");

            // -- AP idle
            emitter().emit("<wvobject type=\"logic\" fp_name=\"/%s/%s/ap_idle\">", hierarchy, name);
            {
                emitter().increaseIndentation();

                emitter().emit("<obj_property name=\"ElementShortName\">ap_idle</obj_property>");
                emitter().emit("<obj_property name=\"ObjectShortName\">ap_idle</obj_property>");

                emitter().decreaseIndentation();
            }
            emitter().emit("</wvobject>");

            // -- AP ready
            emitter().emit("<wvobject type=\"logic\" fp_name=\"/%s/%s/ap_ready\">", hierarchy, name);
            {
                emitter().increaseIndentation();

                emitter().emit("<obj_property name=\"ElementShortName\">ap_ready</obj_property>");
                emitter().emit("<obj_property name=\"ObjectShortName\">ap_ready</obj_property>");

                emitter().decreaseIndentation();
            }
            emitter().emit("</wvobject>");


            // -- AP return
            emitter().emit("<wvobject type=\"array\" fp_name=\"/%s/%s/ap_return\">", hierarchy, name);
            {
                emitter().increaseIndentation();

                emitter().emit("<obj_property name=\"ElementShortName\">ap_return[%d:0]</obj_property>", 31);
                emitter().emit("<obj_property name=\"ObjectShortName\">ap_return[%d:0]</obj_property>", 31);
                emitter().emit("<obj_property name=\"Radix\">UNSIGNEDDECRADIX</obj_property>");

                emitter().decreaseIndentation();
            }
            emitter().emit("</wvobject>");

            emitter().decreaseIndentation();
        }
        emitter().emit("</wvobject>");

        return currentGroupCounter;
    }

}
