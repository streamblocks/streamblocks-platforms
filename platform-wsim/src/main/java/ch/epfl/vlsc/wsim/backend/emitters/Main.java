package ch.epfl.vlsc.wsim.backend.emitters;


import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import ch.epfl.vlsc.wsim.backend.WSimBackend;

import java.nio.file.Path;

@Module
public interface Main {
    @Binding(BindingKind.INJECTED)
    WSimBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateMain() {
        Path mainTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve("main.cpp");
        emitter().open(mainTarget);

        // -- Flatten Network
        Network network = backend().task().getNetwork();

        // -- Includes
        defineIncludes(network);

        // -- Main
        defineMain(network);

        emitter().close();
    }

    default void defineIncludes(Network network) {

        backend().includeSystem("raft");
        backend().includeSystem("cmd");
        backend().includeUser("options.h");
        emitter().emitNewLine();


        emitter().emit("// -- Instance headers");
        for (Instance instance : network.getInstances()) {
            String headerName = instance.getInstanceName() + ".h";

            backend().includeUser(headerName);
        }
        emitter().emitNewLine();
    }

    default void defineMain(Network network) {
        emitter().emit("int main(int argc, char *argv[]) {");
        {
            emitter().increaseIndentation();


            // -- Instances
            for(Instance instance : network.getInstances()){
                emitter().emit("%s i_%1$s;", instance.getInstanceName());
            }

            // -- Network
            emitter().emit("raft::map net;");
            emitter().emitNewLine();


            // -- Options
            emitter().emit("CmdArgs cmdargs(argv[0],std::cout,std::cerr);");
            emitter().emitNewLine();

            // -- Add options
            emitter().emit("sb::options::add_options(cmdargs);");
            emitter().emitNewLine();

            // -- Process options
            emitter().emit("cmdargs.processArgs(argc, argv);");
            emitter().emit("if (sb::options::help || !cmdargs.allMandatorySet()) {");
            {
                emitter().increaseIndentation();
                emitter().emit("cmdargs.printArgs();");
                emitter().emit("exit(EXIT_SUCCESS);");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");


            // -- Connections
            for(Connection connection : network.getConnections()){
                Connection.End src = connection.getSource();
                Connection.End tgt = connection.getTarget();

                emitter().emit("net += i_%s[\"%s\"] >> i_%s[\"%s\"];", src.getInstance().get(), src.getPort(), tgt.getInstance().get(), tgt.getPort());
            }
            emitter().emitNewLine();

            // -- Execute
            emitter().emit("net.exe();");
            emitter().emitNewLine();

            emitter().emit("return (EXIT_SUCCESS);");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

}
