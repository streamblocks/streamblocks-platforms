package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.hls.backend.ExternalMemory;
import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.Map;

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
        ImmutableList<Memories.InstanceVarDeclPair> pairs = backend()
                .externalMemory().getExternalMemories(network);

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

                    // -- External Memories



                    for (Memories.InstanceVarDeclPair pair : pairs) {
                        String memName = backend().externalMemory().namePair(pair);
                        xmlPort("m_axi_" + memName, "master", "0xFFFFFFFF",
                                backend().externalMemory().getAxiWidth(pair));
                    }

                    // -- Input ports
                    for (PortDecl port : network.getInputPorts()) {
                        Type type = backend().types().declaredPortType(port);
                        int bitSize = backend().typeseval().sizeOfBits(type);
                        xmlPort("m_axi_" + port.getName(), "master", "0xFFFFFFFF",
                                Math.max(bitSize, AxiConstants.IO_STAGE_BUS_WIDTH));
                    }

                    // -- Output ports
                    for (PortDecl port : network.getOutputPorts()) {
                        Type type = backend().types().declaredPortType(port);
                        int bitSize = backend().typeseval().sizeOfBits(type);
                        xmlPort("m_axi_" + port.getName(), "master", "0xFFFFFFFF",
                                Math.max(bitSize, AxiConstants.IO_STAGE_BUS_WIDTH));
                    }

                    // -- AXI4-Lite Control
                    xmlPort("s_axi_control", "slave", "0x1000", 32);

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
                    for (PortDecl port : network.getInputPorts()) {
                        xmlArg(idCounter++, port.getName() + "_requested_size", 0, "s_axi_control", "0x0", "0x4", String.format("0x%X", offset), "0x4", "unsigned int");
                        offset += 8;
                    }

                    for (PortDecl port : network.getOutputPorts()) {
                        xmlArg(idCounter++, port.getName() + "_available_size", 0, "s_axi_control", "0x0", "0x4", String.format("0x%X", offset), "0x4", "unsigned int");
                        offset += 8;
                    }
                    // -- Increase by 4 for reserved offset




                    for (PortDecl port : network.getInputPorts()) {
                        Type type = backend().types().declaredPortType(port);
                        xmlArg(idCounter++, port.getName() + "_size", 1, "m_axi_" + port.getName(), "0x0", "0x8", String.format("0x%X", offset), "0x8", "unsigned int*");
                        offset += 12;
                        xmlArg(idCounter++, port.getName() + "_buffer", 1, "m_axi_" + port.getName(), "0x0", "0x8", String.format("0x%X", offset), "0x8", backend().typeseval().KernelXmlType(type) + "*");
                        offset += 12;
                    }

                    // -- Output ports
                    for (PortDecl port : network.getOutputPorts()) {
                        Type type = backend().types().declaredPortType(port);
                        xmlArg(idCounter++, port.getName() + "_size", 1, "m_axi_" + port.getName(), "0x0", "0x8", String.format("0x%X", offset), "0x8", "unsigned int*");
                        offset += 12;
                        xmlArg(idCounter++, port.getName() + "_buffer", 1, "m_axi_" + port.getName(), "0x0", "0x8", String.format("0x%X", offset), "0x8", backend().typeseval().KernelXmlType(type) + "*");
                        offset += 12;
                    }
                    xmlArg(idCounter++, "kernel_command", 0, "s_axi_control", "0x0", "0x8", String.format("0x%x", offset), "0x8", "unsigned long int");
                    offset += 12;

                    // -- External memory args

                    for (Memories.InstanceVarDeclPair pair : pairs) {
                        String memName = backend().externalMemory().namePair(pair);
                        VarDecl decl = pair.getDecl();
                        xmlArg(idCounter++, memName, 1, "m_axi_" + memName,
                                "0x0", "0x8", String.format("0x%X", offset), "0x8", "unsigned int*");
                        offset += 12;
                    }

                    emitter().decreaseIndentation();
                }
                emitter().emit("</args>");
                emitter().emit("<compileWorkGroupSize x=\"1\" y=\"1\" z=\"1\"/>");
                emitter().emit("<maxWorkGroupSize x=\"1\" y=\"1\" z=\"1\"/>");
            }
            emitter().decreaseIndentation();

            emitter().emit("</kernel>");
        }

        emitter().decreaseIndentation();

        emitter().emit("</root>");

        emitter().close();
    }


    default void xmlPort(String name, String mode, String range, int dataWidth) {
        emitter().emit("<port name=\"%s\" portType=\"addressable\" mode=\"%s\" base=\"0x0\" range=\"%s\" dataWidth=\"%d\"/>", name, mode, range, dataWidth);
    }

    default void xmlArg(int id, String name, int addressQualifier, String port, String hostOffset, String hostSize, String offset, String size, String type) {
        emitter().emit("<arg id=\"%d\" name=\"%s\" addressQualifier=\"%d\" port=\"%s\" hostOffset=\"%s\" hostSize=\"%s\" offset=\"%s\" size=\"%s\" type=\"%s\"/>", id, name, addressQualifier, port, hostOffset, hostSize, offset, size, type);
    }


}
