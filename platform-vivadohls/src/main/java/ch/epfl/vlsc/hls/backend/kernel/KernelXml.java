package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.Type;

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
                        Type type = backend().types().declaredPortType(port);
                        int bitSize = TypeUtils.sizeOfBits(type);
                        emitter().emit("<port name=\"m_axi_%s\" mode=\"master\" range=\"0xFFFFFFFFFFFFFFFF\" dataWidth=\"%d\" portType=\"addressable\" base=\"0x0\"/>", port.getName(), Math.max(bitSize, 32));
                    }

                    // -- Output ports
                    for(PortDecl port : network.getOutputPorts()){
                        Type type = backend().types().declaredPortType(port);
                        int bitSize = TypeUtils.sizeOfBits(type);
                        emitter().emit("<port name=\"m_axi_%s\" mode=\"master\" range=\"0xFFFFFFFFFFFFFFFF\" dataWidth=\"%d\" portType=\"addressable\" base=\"0x0\"/>", port.getName(), Math.max(bitSize, 32));
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
                        emitter().emit("<arg id=\"%d\" name=\"%s_requested_size\" addressQualifier=\"0\" port=\"s_axi_control\" size=\"0x4\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"0x4\" type=\"uint\"/>", idCounter++, port.getName() , String.format("%03x", offset));
                        offset+=8;
                    }

                    for(PortDecl port : network.getOutputPorts()){
                        emitter().emit("<arg id=\"%d\"  name=\"%s_available_size\" addressQualifier=\"0\" port=\"s_axi_control\" size=\"0x4\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"0x4\" type=\"uint\"/>", idCounter++, port.getName(),  String.format("%03x", offset));
                        offset+=8;
                    }
                    // -- Increase by 4 for reserved offset
                    for(PortDecl port : network.getInputPorts()){
                        Type type = backend().types().declaredPortType(port);
                        emitter().emit("<arg id=\"%d\" name=\"%s_size\" addressQualifier=\"1\" port=\"m_axi_%2$s\" size=\"%s\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"%s\" type=\"%s*\"/>", idCounter++, port.getName(),  String.format("0x%01x", 8), String.format("0x%03X", offset), String.format("0x%01x", 8), "unsigned int");
                        offset+=12;
                        emitter().emit("<arg id=\"%d\" name=\"%s_buffer\" addressQualifier=\"1\" port=\"m_axi_%2$s\" size=\"%s\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"%s\" type=\"%s*\"/>", idCounter++, port.getName(),  String.format("0x%01x", 8), String.format("0x%03X", offset), String.format("0x%01x", 8), backend().typeseval().KernelXmlType(type));
                        offset+=12;
                    }

                    // -- Output ports
                    for(PortDecl port : network.getOutputPorts()){
                        Type type = backend().types().declaredPortType(port);
                        emitter().emit("<arg id=\"%d\" name=\"%s_size\" addressQualifier=\"1\"  port=\"m_axi_%2$s\" size=\"%s\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"%s\" type=\"%s*\"/>", idCounter++, port.getName(),  String.format("0x%01x", 8), String.format("0x%03X", offset), String.format("0x%01x", 8), "unsigned int");
                        offset+=12;
                        emitter().emit("<arg id=\"%d\" name=\"%s_buffer\" addressQualifier=\"1\" port=\"m_axi_%2$s\" size=\"%s\" offset=\"%s\" hostOffset=\"0x0\" hostSize=\"%s\" type=\"%s*\"/>", idCounter++, port.getName(),  String.format("0x%01x", 8), String.format("0x%03X", offset), String.format("0x%01x", 8), backend().typeseval().KernelXmlType(type));
                        offset+=12;
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
