package ch.epfl.vlsc.hls.backend.wrapper;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;

@Module
public interface KernelXml {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void getKernelXml() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve("kernel.xml"));
        emitter().emit("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        emitter().emit("<root versionMajor=\"1\" versionMinor=\"0\">");
        {
            emitter().increaseIndentation();

            emitter().emit("<kernel name=\"%s_kernel\" language=\"ip_c\" vlnv=\"epfl.ch:kernel:%1$s_kernel:1.0\" attributes=\"\" preferredWorkGroupSizeMultiple=\"0\" workGroupSize=\"1\" interrupt=\"true\">", identifier);
            {
                emitter().increaseIndentation();
                // -- Ports
                emitter().emit("<ports>");
                {
                    emitter().increaseIndentation();
                    // -- AXI4-Lite Control
                    emitter().emit("<port name=\"s_axi_control\" mode=\"slave\" range=\"0x1000\" dataWidth=\"32\" portType=\"addressable\" base=\"0x0\"/>");

                    // -- Input ports
                    for(PortDecl port : network.getInputPorts()){
                        emitter().emit("<port name=\"m_axi_%s\" mode=\"master\" range=\"0xFFFFFFFFFFFFFFFF\" dataWidth=\"%d\" portType=\"addressable\" base=\"0x0\"/>", port.getName(), 256);
                    }

                    // -- Output ports
                    for(PortDecl port : network.getOutputPorts()){
                        emitter().emit("<port name=\"m_axi_%s\" mode=\"master\" range=\"0xFFFFFFFFFFFFFFFF\" dataWidth=\"%d\" portType=\"addressable\" base=\"0x0\"/>", port.getName(), 256);
                    }
                    emitter().decreaseIndentation();
                }
                emitter().emit("</ports>");
                // -- Args
                emitter().emit("<args>");
                {
                    emitter().increaseIndentation();
                    // -- FIXME: size, hostSize, Types
                    int idCounter = 0;
                    int offset = 16;
                    // -- Input ports
                    for(PortDecl port : network.getInputPorts()){
                        emitter().emit("<arg name=\"%s_requested_size\" addressQualifier=\"0\" id=\"%d\" port=\"s_axi_control\" size=\"0x4\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"0x4\" type=\"uint\"/>", port.getName(), idCounter++, String.format("%03x", offset));
                        offset+=4;
                    }
                    // -- Increase by 4 for reserved offset
                    offset+=4;
                    for(PortDecl port : network.getInputPorts()){
                        emitter().emit("<arg name=\"%s_size\" addressQualifier=\"1\" id=\"%d\" port=\"m_axi_%1$s\" size=\"%s\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"%s\" type=\"%s\"/>", port.getName(), idCounter++, String.format("0x%01x", 8), String.format("0x%03X", offset), String.format("%01x", 8), "int*");
                        offset+=8;
                        emitter().emit("<arg name=\"%s_buffer\" addressQualifier=\"1\" id=\"%d\" port=\"m_axi_%1$s\" size=\"%s\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"%s\" type=\"%s\"/>", port.getName(), idCounter++, String.format("0x%01x", 8), String.format("0x%03X", offset), String.format("%01x", 8), "int*");
                        offset+=8;
                    }

                    // -- Output ports
                    for(PortDecl port : network.getOutputPorts()){
                        emitter().emit("<arg name=\"%s_size\" addressQualifier=\"1\" id=\"%d\" port=\"m_axi_%1$s\" size=\"%s\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"%s\" type=\"%s\"/>", port.getName(), idCounter++, String.format("0x%01x", 8), String.format("0x%03X", offset), String.format("%01x", 8), "int*");
                        offset+=8;
                        emitter().emit("<arg name=\"%s_buffer\" addressQualifier=\"1\" id=\"%d\" port=\"m_axi_%1$s\" size=\"%s\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"%s\" type=\"%s\"/>", port.getName(), idCounter++, String.format("0x%01x", 8), String.format("0x%03X", offset), String.format("%01x", 8), "int*");
                        offset+=8;
                    }

                    emitter().decreaseIndentation();
                }
                emitter().emit("</args>");
            }
            emitter().decreaseIndentation();

            emitter().emit("</kernel>");
        }

        emitter().decreaseIndentation();

        emitter().emit("</root>");

        emitter().close();
    }
}
