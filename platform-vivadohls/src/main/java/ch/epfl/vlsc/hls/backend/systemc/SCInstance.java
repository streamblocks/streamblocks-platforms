package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Optional;
import java.util.stream.Stream;

public class SCInstance implements SCInstanceIF {

    public static String makeName(String instanceName) {
        return instanceName + "_verimodule";
    }

    public static class IoIF implements SCIF {
        private final PortIF io;

        public IoIF(ImmutableList<InputIF> readers, ImmutableList<OutputIF> writers){
            io = new PortIF("io", Signal.of("io", new LogicVector(128)));
        }

        @Override
        public Stream<PortIF> stream() {
            return Stream.of(io);
        }

        public PortIF getIo(){
            return io;
        }
    }


    public static class OutputIF implements SCIF {
        private final Queue.WriterIF writer;
        private final PortIF capacity;
        public final PortIF count;

        public OutputIF(Queue queue, String prefix1, String prefix2) {
            this.writer = queue.getWriter().withPrefix(prefix1);
            this.capacity = queue.getAuxiliary().withPrefix(prefix2).getCapacity();
            this.count = queue.getAuxiliary().withPrefix(prefix2).getCount();
        }


        @Override
        public Stream<PortIF> stream() {
            return Stream.concat(writer.stream(), Stream.of(capacity, count));
        }

        public Queue.WriterIF getWriter() {
            return writer;
        }
    }

    public static class InputIF implements SCIF {
        private final Queue.ReaderIF reader;
        private final PortIF peek;
        private final PortIF count;

        public InputIF(Queue queue, String prefix1, String prefix2) {
            this.reader = queue.getReader().withPrefix(prefix1);
            this.peek = queue.getAuxiliary().withPrefix(prefix2).getPeek();
            this.count = queue.getAuxiliary().withPrefix(prefix2).getCount();
        }

        @Override
        public Stream<PortIF> stream() {
            return Stream.concat(reader.stream(), Stream.of(peek, count));
        }

        public Queue.ReaderIF getReader() {
            return reader;
        }

    }

    private final APControl apControl;
    private final ImmutableList<InputIF> readers;
    private final ImmutableList<OutputIF> writers;
    private final String instanceName;
    private final String name;
    private final PortIF ret;
    private final String originalName;

    private final ImmutableList<String> actionsIds;

    public SCInstance(String name, ImmutableList<InputIF> readers, ImmutableList<OutputIF> writers, ImmutableList<String> actionIds) {
        this.originalName = name;
        this.name = this.makeName(name);
        this.instanceName = "inst_" + name;
        this.apControl = new APControl(this.instanceName + "_");
        this.readers = readers;
        this.writers = writers;
        this.ret = PortIF.of(
                "ap_return",
                Signal.of(name + "_ap_return", new LogicVector(32)),
                Optional.of(PortIF.Kind.OUTPUT));
        this.actionsIds = actionIds;


    }

    @Override
    public Stream<PortIF> stream() {
        return Stream.concat(
                Stream.concat(
                        readers.stream().flatMap(InputIF::stream),
                        writers.stream().flatMap(OutputIF::stream)),
                Stream.concat(
                        Stream.of(
                                apControl.getClock(),
                                apControl.getReset()
                        ),
                        streamUnique()));


    }

    public Stream<PortIF> streamUnique() {
        return
                Stream.of(
                        apControl.getDone(),
                        apControl.getReady(),
                        apControl.getIdle(),
                        apControl.getStart(),
                        ret);
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getName() {
        return name;
    }

    public APControl getApControl() {
        return this.apControl;
    }

    public PortIF getReturn() {
        return this.ret;
    }

    public int getNumActions() {
        return actionsIds.size();
    }

    public ImmutableList<String> getActionsIds() {
        return actionsIds;
    }

    public String getOriginalName() {
        return this.originalName;
    }
}
