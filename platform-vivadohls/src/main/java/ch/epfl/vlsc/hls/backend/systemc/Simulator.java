package ch.epfl.vlsc.hls.backend.systemc;
import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Module
public interface Simulator {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void genrateSimulator(Network network) {


        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve("simulate.cpp"));
        BufferedReader reader;
        try {


            reader = new BufferedReader(
                    new InputStreamReader(getClass().getResourceAsStream("/lib/systemc/simulate.cpp")));
            String line  = reader.readLine();
            while (line != null) {
                emitter().emitRawLine(line);
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not get the SystemC simulator"));
        }

        emitter().emit("int sc_main(int argc, char *argv[]) {");
        {
            emitter().increaseIndentation();
            emitter().emit("Options opts = parse_args(argc, argv);");
            emitter().emit("const sc_time period(opts.period, SC_NS);");
            emitter().emit("using Network= ap_rtl::network_tester;");
            // -- create the network object
            emitter().emit("std::unique_ptr<Network> mut = std::make_unique<Network>(\"network\", period, opts.trace_level);");
            // -- fill sim buffers


            emitter().emit("std::fstream ifs;");
            emitter().emit("std::string line;");
            emitter().emit("std::size_t count = 0;");
//            for (PortDecl port: network.getInputPorts()) {
//                emitter().emit("// -- reading reference inputs for port %s", port.getName());
//                emitter().emit("ifs.open(\"%s.txt\", std::ios::in);", port.getName());
//                emitter().emit("if (ifs.is_open()) {");
//                {
//                    emitter().increaseIndentation();
//
//                    int bitWidth = backend().typeseval().sizeOfBits(backend().types().declaredPortType(port));
//                    LogicVector type = new LogicVector(bitWidth);
//                    emitter().emit("%s token_%s;", type.getType(), port.getName());
//                    emitter().emit("count = 0;");
//                    emitter().emit("while (ifs >> token_%s) {", port.getName());
//                    {
//                        emitter().increaseIndentation();
//                        emitter().emit("mut->sim_buffer_%s->buffer->push_back(token_%1$s);", port.getName());
//                        emitter().emit("count ++;");
//                        emitter().decreaseIndentation();
//                    }
//                    emitter().emit("}");
//                    emitter().emit(
//                            "STATUS_REPORT(\"Reference queue %s contains %%lu tokens.\\n\\n\", count);",
//                            port.getName());
//                    emitter().emit("mut->sim_buffer_%s->set_end(count);", port.getName());
//                    emitter().decreaseIndentation();
//
//                }
//                emitter().emit("} else {");
//                {
//
//                    emitter().increaseIndentation();
//                    emitter().emit("PANIC(\"Could not open %s.txt Make sure the file is placed " +
//                            "next to the simulation binary\");", port.getName());
//                    emitter().emitNewLine();
//                    emitter().decreaseIndentation();
//                }
//                emitter().emit("}");
//                emitter().emit("ifs.close();");
//                emitter().emitNewLine();
//            }

            emitter().emitNewLine();

//            for (PortDecl port: network.getOutputPorts()) {
//                emitter().emit("// -- reading reference output for port %s", port.getName());
//                emitter().emit("ifs.open(\"%s.txt\", std::ios::in);", port.getName());
//                emitter().emit("if (ifs.is_open()) {");
//                {
//
//                    emitter().increaseIndentation();
//
//                    int bitWidth = backend().typeseval().sizeOfBits(backend().types().declaredPortType(port));
//                    LogicVector type = new LogicVector(bitWidth);
//                    emitter().emit("%s token_%s;", type.getType(), port.getName());
//                    emitter().emit("count = 0;");
//                    emitter().emit("while (ifs >> token_%s) {", port.getName());
//                    {
//                        emitter().increaseIndentation();
//                        emitter().emit("mut->sim_buffer_%s->buffer->push_back(token_%1$s);", port.getName());
//                        emitter().emit("count ++;");
//                        emitter().decreaseIndentation();
//                    }
//                    emitter().emit("}");
//                    emitter().emit(
//                            "STATUS_REPORT(\"Reference queue %s contains %%lu tokens.\\n\\n\", count);",
//                            port.getName());
//                    emitter().decreaseIndentation();
//                }
//                emitter().emit("} else {");
//                {
//
//                    emitter().increaseIndentation();
//                    emitter().emit("PANIC(\"Could not open %s.txt Make sure the file is placed " +
//                            "next to the simulation binary\");", port.getName());
//                    emitter().emitNewLine();
//                    emitter().decreaseIndentation();
//                }
//                emitter().emit("}");
//                emitter().emit("ifs.close();");
//                emitter().emitNewLine();
//            }


            emitter().emitNewLine();
            emitter().emit("// -- reset the network");
            emitter().emit("mut->reset();");
            emitter().emit("// -- run to completion");
            emitter().emit("mut->simulate();");
            emitter().emit("mut->dump_stats();");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().close();

    }


}