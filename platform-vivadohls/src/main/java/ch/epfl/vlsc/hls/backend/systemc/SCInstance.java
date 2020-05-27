package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Optional;
import java.util.stream.Stream;

class SCInstance implements SCIF {

    public static class OutputIF implements SCIF {
        public final Queue.WriterIF writer;
        public final PortIF capacity;
        public final PortIF count;
        public OutputIF(Queue queue, String prefix) {
            this.writer = queue.getWriter().withPrefix(prefix);
            this.capacity = queue.getAuxiliary().withPrefix(prefix).capacity;
            this.count = queue.getAuxiliary().withPrefix(prefix).count;
        }

        public static OutputIF of(Queue queue, String prefix) {
            return new OutputIF(queue, prefix);
        }
        @Override
        public Stream<PortIF> stream() {
            return Stream.concat(writer.stream(), Stream.of(capacity, count));
        }
    }
    public static class InputIF implements SCIF {
        public final Queue.ReaderIF reader;
        public final PortIF peek;
        public final PortIF count;

        public InputIF(Queue queue, String prefix) {
            this.reader = queue.getReader().withPrefix(prefix);
            this.peek = queue.getAuxiliary().withPrefix(prefix).peek;
            this.count = queue.getAuxiliary().withPrefix(prefix).count;
        }
        public static InputIF of(Queue queue, String prefix) {
            return new InputIF(queue, prefix);
        }
        @Override
        public Stream<PortIF> stream() {
            return Stream.concat(reader.stream(), Stream.of(peek, count));
        }
    }
    private final APControl apControl;
    private final ImmutableList<InputIF> readers;
    private final ImmutableList<OutputIF> writers;
    private final String instanceName;
    private final String name;
    private final PortIF ret;

    public SCInstance (String name, ImmutableList<InputIF> readers, ImmutableList<OutputIF> writers) {
        this.name = name;
        this.instanceName = "inst_" + name;
        this.apControl = new APControl(name + "_");
        this.readers = readers;
        this.writers = writers;
        this.ret = PortIF.of(
                "ap_return",
                Signal.of(name + "_ap_return", new LogicVector(32)),
                Optional.of(PortIF.Kind.OUTPUT));

    }

    @Override
    public Stream<PortIF> stream() {
        return Stream.concat(
                readers.stream().flatMap(InputIF::stream),
                Stream.concat(
                        writers.stream().flatMap(OutputIF::stream),
                        Stream.concat (
                                apControl.stream(),
                                Stream.of(ret))));

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

}
