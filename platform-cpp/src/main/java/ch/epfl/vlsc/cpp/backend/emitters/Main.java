package ch.epfl.vlsc.cpp.backend.emitters;


import ch.epfl.vlsc.configuration.Configuration;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.expr.ExprList;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import ch.epfl.vlsc.cpp.backend.CppBackend;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Module
public interface Main {
    @Binding(BindingKind.INJECTED)
    CppBackend backend();

    @Binding(BindingKind.LAZY)
    default Map<Connection.End, Integer> connectionEndNbrReaders() {
        return new HashMap<>();
    }

    @Binding(BindingKind.LAZY)
    default Map<Connection.End, Integer> connectionId() {
        return new HashMap<>();
    }

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

        // -- System
        backend().includeSystem("cstdint");
        backend().includeSystem("thread");
        backend().includeSystem("chrono");
        backend().includeSystem("map");
        backend().includeSystem("set");
        backend().includeSystem("vector");
        backend().includeSystem("iostream");
        backend().includeSystem("string");
        backend().includeSystem("functional");
        backend().includeSystem("memory");
        emitter().emitNewLine();

        // -- User
        backend().includeUser("mapping_parser.h");
        backend().includeUser("get_opt.h");
        backend().includeUser("actor.h");
        backend().includeUser("fifo.h");
        emitter().emitNewLine();

        // -- Trace header and Firing Id
        emitter().emit("#ifdef TRACE_TURNUS");
        backend().includeSystem("fstream");
        emitter().emit("#include \"turnus_tracer.h\"");
        emitter().emit("long long firingId = 0;");
        emitter().emit("#endif");
        emitter().emitNewLine();

        // -- Profiling
        emitter().emit("#ifdef WEIGHT_PROFILING");
        backend().includeUser("profiling_data.h");
        emitter().emit("#endif");
        emitter().emitNewLine();

        emitter().emit("// -- Instance headers");
        for (Instance instance : network.getInstances()) {
            String headerName = instance.getInstanceName() + ".h";

            backend().includeUser(headerName);
        }
        emitter().emitNewLine();

        // -- Chrono
        emitter().emit("// -- Chrono");
        emitter().emit("using Clock = std::chrono::high_resolution_clock;");
        emitter().emit("using Ms = std::chrono::milliseconds;");
        emitter().emit("template<class Duration>");
        emitter().emit("using TimePoint = std::chrono::time_point<Clock, Duration>;");
        emitter().emitNewLine();

        // -- Initial tokens
        emitter().emit("// -- Initial tokens helper funciton");
        emitter().emit("template<typename T, int size>");
        emitter().emit("void write_initial_tokens(Fifo<T> *fifo, std::array<T, size> data){");

        emitter().increaseIndentation();
        {
            emitter().emit("T* addr = fifo->write_address();");
            emitter().emit("size_t p = 0;");
            emitter().emitNewLine();

            emitter().emit("for(const auto& token : data){");
            emitter().emit("\taddr[p] = token;");
            emitter().emit("\tp++;");
            emitter().emit("}");
            emitter().emitNewLine();

            emitter().emit("if (size==1) {");
            emitter().emit("\tfifo->write_advance();");
            emitter().emit("} else {");
            emitter().emit("\tfifo->write_advance(size);");
            emitter().emit("}");
        }
        emitter().decreaseIndentation();

        emitter().emit("}");
        emitter().emitNewLine();

        // -- Partitions Prototype
        emitter().emit("// -- Partitions Prototype");
        emitter().emit("void partition_singlecore(std::vector<Actor*> actors);");
        emitter().emit("void partition(std::string name, std::vector<Actor*> actors);");
        emitter().emitNewLine();

        // -- Single Partition
        defineSingleCorePartitioning(network);
        emitter().emitNewLine();

        // -- Multiple Partitions
        defineMultiplePartitioning();
        emitter().emitNewLine();
    }

    default void defineSingleCorePartitioning(Network network) {
        emitter().emit("void partition_singlecore(std::vector<Actor*> actors) {");
        {
            emitter().increaseIndentation();

            emitter().emit("bool run = false;");
            emitter().emit("EStatus status = None;");
            emitter().emit("do {");
            {
                emitter().increaseIndentation();

                emitter().emit("run = false;");
                emitter().emit("for (Actor* actor : actors) {");
                emitter().emit("\trun |= actor->action_selection(status);");
                emitter().emit("}");

                emitter().decreaseIndentation();
            }
            emitter().emit("} while(run);");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void defineMultiplePartitioning() {
        emitter().emit("void partition(std::string name, std::vector<Actor*> actors) {");
        {
            emitter().increaseIndentation();

            emitter().emit("EStatus status = None;");
            emitter().emit("Clock::time_point _start = Clock::now();");
            emitter().emit("bool stop = false;");
            emitter().emitNewLine();

            emitter().emit("do {");
            {
                emitter().increaseIndentation();

                emitter().emit("for (Actor* actor : actors) {");
                emitter().emit("\tactor->action_selection(status);");
                emitter().emit("}");
                emitter().emitNewLine();

                emitter().emit("if (status == None) {");
                {
                    emitter().increaseIndentation();

                    emitter().emit("stop = TimePoint<Ms>(std::chrono::duration_cast < Ms > (Clock::now() - _start)) > TimePoint<Ms>(Ms(1000));");
                    emitter().emit("std::this_thread::yield();");
                    emitter().emitNewLine();
                    emitter().emit("if (stop) {");
                    emitter().emit("\tstd::cout << \"Time out occurred on partition \" << name << \"!\" << std::endl;");
                    emitter().emit("}");

                    emitter().decreaseIndentation();
                }
                emitter().emit("} else {");
                emitter().emit("\t_start = Clock::now();");
                emitter().emit("}");

                emitter().decreaseIndentation();
            }
            emitter().emit("} while (!stop);");


            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void fifoAllocationAndPointerAssignment() {
        emitter().emit("// -- FIFO Allocation and connection");
        Network network = backend().task().getNetwork();

        int countId = 0;
        for (Connection connection : network.getConnections()) {
            Connection.End source = connection.getSource();
            Optional<Connection.End> f = connectionEndNbrReaders().keySet()
                    .stream()
                    .filter(e -> e.getInstance().equals(source.getInstance()) && e.getPort().equals(source.getPort()))
                    .findAny();
            if (!f.isPresent()) {
                connectionEndNbrReaders().put(source, 1);
                connectionId().put(source, countId);
                countId++;
            } else {
                Connection.End c = f.get();
                Integer r = connectionEndNbrReaders().get(c) + 1;
                connectionEndNbrReaders().put(c, r);
            }
        }

        for (Connection.End c : connectionId().keySet()) {
            emitter().emit("auto *fifo_%s = new Fifo<%s >(%s, %s, %s);",
                    connectionId().get(c),
                    backend().typeseval().type(backend().channelUtils().sourceEndType(c)),
                    backend().channelUtils().connectionBufferSize(backend().channelUtils().targetEndConnections(c).get(0)),
                    // FIXME : get correct threshold
                    backend().channelUtils().connectionBufferSize(backend().channelUtils().targetEndConnections(c).get(0)),
                    connectionEndNbrReaders().get(c)
            );
        }
        emitter().emitNewLine();

        for (Connection c : network.getConnections()) {
            Optional<ToolValueAttribute> attribute = c.getValueAttribute("initialTokens");
            if (attribute.isPresent()) {
                emitter().emit("// -- Initial tokens for fifo_%s", connectionId().get(c.getSource()));

                String evaluate = backend().expressions().evaluate(attribute.get().getValue());
                String type = backend().typeseval().type(backend().channelUtils().sourceEndType(c.getSource()));
                int size = ((ExprList) attribute.get().getValue()).getElements().size();
                emitter().emit("write_initial_tokens< %s, %s >(fifo_%s, %s);", type, size, connectionId().get(c.getSource()), evaluate);

                emitter().emitNewLine();

            }
        }

        for (Connection.End source : connectionId().keySet()) {
            int id = connectionId().get(source);
            List<Connection> targetConnections = backend().channelUtils().targetEndConnections(source);
            if(source.getInstance().isPresent()) {
                emitter().emit("i_%s->port_%s = fifo_%d;", source.getInstance().get(), source.getPort(), id);
            }
            for (Connection c : targetConnections) {
                if(c.getTarget().getInstance().isPresent()) {
                    emitter().emit("i_%s->port_%s = fifo_%d;", c.getTarget().getInstance().get(), c.getTarget().getPort(), id);
                }
            }
            emitter().emitNewLine();
        }

    }

    default void defineActorMap(Network network) {
        emitter().emit("// -- Actors Map");
        emitter().emit("std::vector<Actor*> actors;");
        for (Instance instance : network.getInstances()) {
            emitter().emit("actors.push_back(i_%s);", instance.getInstanceName());
        }
        emitter().emitNewLine();
    }

    default void definePartition(){
        emitter().emit("// -- Partitions");
        emitter().emit("std::map<std::string, std::vector<Actor*>> partitions;");
        emitter().emit("MappingParser parser(sb::options::config_file, actors);");
        emitter().emit("partitions = parser.getPartitions();");
        emitter().emitNewLine();

        emitter().emit("int partitions_size = partitions.size();");
        emitter().emit("if (partitions_size > 1) {");
        {
            emitter().increaseIndentation();

            emitter().emit("std::vector<std::thread> workers(partitions_size);");
            emitter().emitNewLine();

            emitter().emit("int i = 0;");
            emitter().emit("for (std::map<std::string, std::vector<Actor*>>::iterator it=partitions.begin(); it!=partitions.end(); ++it){");
            {
                emitter().increaseIndentation();

                emitter().emit("workers[i] = std::thread( partition, it->first, it->second );");
                emitter().emit("i++;");

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            emitter().emit("for (int i = 0; i < partitions_size; i++) {");
            emitter().emit("\tworkers[i].join();");
            emitter().emit("}");


            emitter().decreaseIndentation();
        }
        emitter().emit("} else {");
        emitter().emit("\tpartition_singlecore(actors);");
        emitter().emit("}");
        emitter().emitNewLine();
    }


    default void defineMain(Network network) {
        emitter().emit("int main(int argc, char *argv[]) {");
        {
            emitter().increaseIndentation();

            // -- Instantiate Actors
            emitter().emit("// -- Instantiate Actors");
            for (Instance instance : network.getInstances()) {
                emitter().emit("auto *i_%s = new %1$s();", instance.getInstanceName());
            }
            emitter().emitNewLine();



            emitter().emit("#ifdef TRACE_TURNUS");
            emitter().emit("// -- Set tracer");
            emitter().emit("TurnusTracer *tracer = new TurnusTracer(\"%s.etracez\");", backend().task().getIdentifier());
            for (Instance instance : network.getInstances()) {
                emitter().emit("i_%s->set_tracer(tracer);", instance.getInstanceName());
            }
            emitter().emit("#endif");
            emitter().emitNewLine();

            emitter().emit("#ifdef WEIGHT_PROFILING");
            emitter().emit("ProfilingData* profiling_data = new ProfilingData(\"%s\");", backend().task().getIdentifier());
            for (Instance instance : network.getInstances()) {
                emitter().emit("i_%s->set_profiling_data(profiling_data);", instance.getInstanceName());
            }
            emitter().emit("#endif");
            emitter().emitNewLine();


            // -- Instantiate FIFOs
            fifoAllocationAndPointerAssignment();

            // -- Actors Map
            defineActorMap(network);

            // ++ Get Options
            emitter().emit("// -- Get Options");
            emitter().emit("GetOpt options = GetOpt(argc, argv);");
            emitter().emit("std::string application_name = \"%s\";", backend().task().getIdentifier());
            emitter().emit("options.setActors(actors);");
            emitter().emit("options.getOptions();");
            emitter().emitNewLine();

            // -- Partition
            definePartition();

            emitter().emit("#ifdef TRACE_TURNUS");
            emitter().emit("// -- Write trace");
            emitter().emit("tracer->write();");
            emitter().emitNewLine();
            emitter().emit("// -- Write firings in a file");
            emitter().emit("std::ofstream trace_profiling_info;");
            emitter().emit("trace_profiling_info.open(\"%s.info\");", backend().task().getIdentifier());
            emitter().emit("trace_profiling_info << \"firings=\" << firingId << \"\\n\";");
            emitter().emit("trace_profiling_info.close();");
            emitter().emit("delete tracer;");
            emitter().emit("#endif");
            emitter().emitNewLine();

            emitter().emit("#ifdef WEIGHT_PROFILING");
            emitter().emit("profiling_data->generate_results(\"\", false, true);");
            emitter().emit("delete profiling_data;");
            emitter().emit("#endif");
            emitter().emitNewLine();

            // -- Delete
            for (Instance instance : network.getInstances()) {
                emitter().emit("delete i_%s;", instance.getInstanceName());
            }
            emitter().emitNewLine();


            for(Connection.End c : connectionId().keySet()){
                emitter().emit("delete fifo_%s;", connectionId().get(c));
            }

            emitter().emitNewLine();
            emitter().emit("return (EXIT_SUCCESS);");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

}
