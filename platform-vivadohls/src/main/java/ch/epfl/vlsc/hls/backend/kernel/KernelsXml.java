package ch.epfl.vlsc.hls.backend.kernel;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;

import java.util.Optional;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.Type;

@Module
public interface KernelsXml {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default int defaultPipeDepth() {
        return 16;
    }

    default void getKernelXml(String kernelType) {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // kernel name

        String kernelName = identifier + "_" + kernelType;
        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenXml(backend().context())
                .resolve(identifier + "_" + kernelType + "_kernel.xml"));
        emitter().emit("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        emitter().emit("<root versionMajor=\"1\" versionMinor=\"0\">");
        {
            emitter().increaseIndentation();

            emitter().emit(
                    "<kernel name=\"%s_kernel\" language=\"ip_c\" vlnv=\"epfl.ch:kernel:%1$s_kernel:1.0\" attributes=\"\" preferredWorkGroupSizeMultiple=\"0\" workGroupSize=\"1\" interrupt=\"true\">",
                    kernelName);
            {
                emitter().increaseIndentation();
                // -- Ports
                emitter().emit("<ports>");
                {
                    emitter().increaseIndentation();

                    // -- Input ports
                    if (kernelType == "input") {
                        for (PortDecl port : network.getInputPorts()) {

                            xmlAxiPort("m_axi_" + port.getName(), "master", "0xFFFFFFFF",
                                    Math.max(getBitSize(port), 32));
                            xmlStreamPort(backend().kernel().getPipeName(port), "write_only", getBitSize(port));
                        }

                    } else if (kernelType == "output") {
                        // -- Output ports
                        for (PortDecl port : network.getOutputPorts()) {

                            xmlAxiPort("m_axi_" + port.getName(), "master", "0xFFFFFFFF",
                                    Math.max(getBitSize(port), 32));
                            xmlStreamPort(backend().kernel().getPipeName(port), "read_only", getBitSize(port));
                        }

                    } else if (kernelType == "core") {

                        for (PortDecl port : network.getInputPorts()) {

                            xmlStreamPort(backend().kernel().getPipeName(port), "read_only", getBitSize(port));
                        }
                        for (PortDecl port : network.getOutputPorts()) {

                            xmlStreamPort(backend().kernel().getPipeName(port), "write_only", getBitSize(port));
                        }

                    }
                    // -- AXI4-Lite Control
                    xmlAxiPort("s_axi_control", "slave", "0x1000", 32);

                    emitter().decreaseIndentation();
                }
                emitter().emit("</ports>");
                // -- Args
                xmlArgs(kernelType);

                emitter().emit("<compileWorkGroupSize x=\"1\" y=\"1\" z=\"1\"/>");
                emitter().emit("<maxWorkGroupSize x=\"1\" y=\"1\" z=\"1\"/>");
            }
            emitter().decreaseIndentation();

            emitter().emit("</kernel>");
            if (kernelType == "core" || kernelType == "input") {
                for (PortDecl port : network.getInputPorts()) {
                    xmlPipe("xcl_pipe_" + backend().kernel().getPipeName(port), getBitSize(port), defaultPipeDepth());
                    xmlConnection(kernelName + "_kernel", backend().kernel().getPipeName(port),
                            "xcl_pipe_" + backend().kernel().getPipeName(port),
                            kernelType == "core" ? "M_AXIS" : "S_AXIS", "kernel", "pipe");
                }

            }
            if (kernelType == "core" || kernelType == "output") {
                for (PortDecl port : network.getOutputPorts()) {
                    xmlPipe("xcl_pipe_" + backend().kernel().getPipeName(port), getBitSize(port), defaultPipeDepth());
                    xmlConnection(kernelName + "_kernel", backend().kernel().getPipeName(port),
                            "xcl_pipe_" + backend().kernel().getPipeName(port),
                            kernelType == "core" ? "S_AXI" : "M_AXIS", "kernel", "pipe");
                }

            }

        }

        emitter().decreaseIndentation();

        emitter().emit("</root>");

        emitter().close();
    }

    default int getBitSize(PortDecl port) {
        Type type = backend().types().declaredPortType(port);
        int bitSize = TypeUtils.sizeOfBits(type);
        return bitSize;
    }

    // axi port
    default void xmlAxiPort(String name, String mode, String range, int dataWidth) {
        emitter().emit(
                "<port name=\"%s\" portType=\"addressable\" mode=\"%s\" base=\"0x0\" range=\"%s\" dataWidth=\"%d\"/>",
                name, mode, range, dataWidth);
    }

    // Stream port
    default void xmlStreamPort(String name, String mode, int dataWidth) {
        emitter().emit("<port name=\"%s\" mode=\"%s\" dataWidth=\"%d\" portType=\"stream\"/>", name, mode,
                getPipeWidth(dataWidth));
    }

    default void xmlArg(int id, String name, int addressQualifier, String port, String hostOffset, String hostSize,
            String offset, String size, String type) {
        emitter().emit(
                "<arg id=\"%d\" name=\"%s\" addressQualifier=\"%d\" port=\"%s\" hostOffset=\"%s\" hostSize=\"%s\" offset=\"%s\" size=\"%s\" type=\"%s\"/>",
                id, name, addressQualifier, port, hostOffset, hostSize, offset, size, type);
    }

    default void xmlPipe(String name, int width, int depth) {
        emitter().emit("<pipe name=\"%s\" width=\"0x%X\" depth=\"0x%X\" linkage=\"internal\"/>", name, width / 8,
                depth);
    }

    default void xmlConnection(String srcInst, String srcPort, String dstInst, String dstPort, String srcType,
            String dstType) {
        emitter().emit(
                "<connection srcInst=\"%s\" srcPort=\"%s\" dstInst=\"%s\" dstPort=\"%s\" srcType=\"%s\" dstType=\"%s\"/>",
                srcInst, srcPort, dstInst, dstPort, srcType, dstType);
    }

    default void xmlArgs(String kernelType) {

        Network network = backend().task().getNetwork();

        Optional<ImmutableList<PortDecl>> args = Optional.empty();

        if (kernelType == "input") {
            args = Optional.of(network.getInputPorts());
        } else if (kernelType == "output") {
            args = Optional.of(network.getOutputPorts());
        }

        emitter().emit("<args>");
        {
            emitter().increaseIndentation();
            // -- FIXME: size, hostSize, Types
            int idCounter = 0;
            int offset = 16;
            // -- Input ports
            if (args.isPresent()) {
                for (PortDecl port : args.get()) {
                    xmlArg(idCounter++,
                            port.getName() + "_" + backend().kernel().requestOrAvailable(kernelType == "input"), 0,
                            "s_axi_control", "0x0", "0x4", String.format("0x%X", offset), "0x4", "unsigned int");
                    offset += 8;
                }
                for (PortDecl port : args.get()) {
                    Type type = backend().types().declaredPortType(port);
                    xmlArg(idCounter++, port.getName() + "_size", 1, "m_axi_" + port.getName(), "0x0", "0x8",
                            String.format("0x%X", offset), "0x8", "unsigned int*");
                    offset += 12;
                    xmlArg(idCounter++, port.getName() + "_buffer", 1, "m_axi_" + port.getName(), "0x0", "0x8",
                            String.format("0x%X", offset), "0x8", backend().typeseval().KernelXmlType(type) + "*");
                    offset += 12;
                }
                for (PortDecl port : args.get()) {
                    int byteSize = getPipeWidth(getBitSize(port)) / 8;
                    xmlPipeArg(port, byteSize, offset, 0, byteSize, defaultPipeDepth() * byteSize);
                    offset += 12;
                }
            } else {
                ImmutableList<PortDecl> allArgs = ImmutableList.concat(network.getInputPorts(),
                        network.getOutputPorts());
                for (PortDecl port : allArgs) {
                    int byteSize = getPipeWidth(getBitSize(port)) / 8;
                    xmlPipeArg(port, byteSize, offset, 0, byteSize, defaultPipeDepth() * byteSize);
                    offset += 12;
                }
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("</args>");
    }

    default void xmlPipeArg(PortDecl port, int size, int offset, int hostOffset, int hostSize, int memSize) {
        emitter().emit(
                "<arg name = \"__xcl_gv_%s\" port=\"%1$s\" addressQualifier=\"4\" id=\"\" size=\"0x%X\" offset=\"0x%X\" hostOffset=\"0x%X\" hostSize=\"0x%X\" type=\"\" memSize=\"0x%X\" origName=\"%1$s\" origUse=\"variable\"/>",
                backend().kernel().getPipeName(port), size, offset, hostOffset, hostSize, memSize);

    }

    default int getPipeWidth(int width) {
        if (width <= 8) {
            return 8;
        } else if (width <= 16) {
            return 16;
        } else if (width <= 32) {
            return 32;
        } else if (width <= 64) {
            return 64;
        } else if (width <= 128) {
            return 128;
        } else if (width <= 256) {
            return 256;
        } else if (width <= 512) {
            return 512;
        } else {
            throw new Error("invalid pipe width of " + width);
        }
    }

}
