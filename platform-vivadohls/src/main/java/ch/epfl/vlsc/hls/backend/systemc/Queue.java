package ch.epfl.vlsc.hls.backend.systemc;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.Optional;
import java.util.stream.Stream;

public class Queue implements SCIF{




    public static class WriterIF implements SCIF{
        private final PortIF din;
        private final PortIF write;
        private final PortIF fullN;
        public  WriterIF(Signal din, String write, String fullN) {
            this("", din, Signal.of(write, new LogicValue()), Signal.of(fullN, new LogicValue()));
        }
        public WriterIF(String prefix_, Signal din, Signal write, Signal fullN) {
            this.din = PortIF.of(
                    prefix_ + "din",
                    din,
                    Optional.of(PortIF.Kind.INPUT));
            this.write = PortIF.of(
                    prefix_ + "write",
                    write,
                    Optional.of(PortIF.Kind.INPUT));
            this.fullN = PortIF.of(
                    prefix_ + "full_n",
                    fullN,
                    Optional.of(PortIF.Kind.OUTPUT));
        }

        public WriterIF withPrefix(String prefix_) {
            return new WriterIF(prefix_, this.din.getSignal(), this.write.getSignal(), this.fullN.getSignal());
        }
        @Override
        public Stream<PortIF> stream() {
            return Stream.of(
                    this.din, this.write, this.fullN
            );
        }
        public PortIF getDin() {
            return  din;
        }
        public PortIF getWrite () {
            return write;
        }
        public PortIF getFullN() {
            return fullN;
        }
    }
    public static class ReaderIF implements SCIF {
        private final PortIF dout;
        private final PortIF read;
        private final PortIF emptyN;
        public ReaderIF(Signal dout, String read, String emptyN) {
            this ("", dout, Signal.of(read, new LogicValue()), Signal.of(emptyN, new LogicValue()));
        }
        public ReaderIF(String prefix_, Signal dout, Signal read, Signal emptyN) {
            this.dout = PortIF.of(prefix_ + "dout", dout, Optional.of(PortIF.Kind.OUTPUT));
            this.read = PortIF.of(prefix_ + "read", read, Optional.of(PortIF.Kind.INPUT));
            this.emptyN = PortIF.of(prefix_ + "empty_n", emptyN, Optional.of(PortIF.Kind.OUTPUT));
        }
        public ReaderIF withPrefix(String prefix_) {
            return new ReaderIF(prefix_, dout.getSignal(), read.getSignal(), emptyN.getSignal());
        }
        @Override
        public Stream<PortIF> stream() {
            return Stream.of(
                    this.dout, this.read, this.emptyN
            );
        }

        public PortIF getDout() {
            return dout;
        }

        public PortIF getEmptyN() {
            return emptyN;
        }

        public PortIF getRead() {
            return read;
        }
    }
    public static class AuxiliaryIF implements SCIF{
        private final PortIF count;
        private final PortIF capacity;
        private final PortIF peek;
        public AuxiliaryIF(Signal count, Signal capacity, Signal peek) {
            this("", count, capacity, peek);
        }

        public AuxiliaryIF(String prefix_, Signal count, Signal capacity, Signal peek) {
            this.count = PortIF.of(prefix_ + "count", count, Optional.of(PortIF.Kind.OUTPUT));
            this.capacity = PortIF.of(prefix_ + "size", capacity, Optional.of(PortIF.Kind.OUTPUT));
            this.peek = PortIF.of(prefix_ + "peek", peek, Optional.of(PortIF.Kind.OUTPUT));
        }
        public AuxiliaryIF withPrefix(String prefix_) {
            return new AuxiliaryIF(prefix_, this.count.getSignal(), this.capacity.getSignal(), this.peek.getSignal());
        }
        @Override
        public Stream<PortIF> stream() {
            return Stream.of(
                    this.count, this.capacity, this.peek
            );
        }
        public PortIF getCount() {
            return count;
        }

        public PortIF getCapacity() {
            return capacity;
        }

        public PortIF getPeek() {
            return peek;
        }
    }
    private final String name;
    private final LogicVector type;
    private final WriterIF writer;
    private final ReaderIF reader;
    private final AuxiliaryIF auxiliary;
    private final int depth;
    private final boolean isInput;
    private final boolean isOutput;
    public Queue(Connection connection, int width, int depth) {

        Connection.End source = connection.getSource();
        Connection.End target = connection.getTarget();
        Optional<PortIF.Kind> writerKind = Optional.empty();
        Optional<PortIF.Kind> readerKind = Optional.empty();
        String prefix = "";
        if (source.getInstance().isPresent() && target.getInstance().isPresent()) {
            prefix = "q_" + source.getInstance().get() + "_" + source.getPort() + "_" +
                    target.getInstance().get() + "_" + target.getPort();
            name = prefix;
            isOutput = false;
            isInput = false;
        } else if (source.getInstance().isPresent() && !target.getInstance().isPresent()) {
            prefix = source.getInstance().get() + "_" + source.getPort() + "_" + target.getPort();
            name = "q_" + prefix;
            isOutput = true;
            isInput = false;
        } else if (!source.getInstance().isPresent() && target.getInstance().isPresent()) {
            prefix = source.getPort() + "_" + target.getInstance().get() + "_" + target.getPort();
            name = "q_" + prefix;
            isOutput = false;
            isInput = true;
        } else {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            String.format("Can not create SystemC queue, " +
                                    "invalid connection ?.%s -> ?.%s", source.getPort(), target.getPort())));
        }

        String src = source.getInstance().orElse("") + "_" + source.getPort();
        String tgt = target.getInstance().orElse("") + "_" + target.getPort();
        this.depth = depth;

        type = new LogicVector(width);
        writer = new WriterIF(
                Signal.of(prefix + "_din", type),
                prefix + "_write",
                prefix + "_full_n");
        reader = new ReaderIF(
                Signal.of(prefix + "_dout", type),
                prefix + "_read",
                prefix + "_empty_n");

        auxiliary = new AuxiliaryIF(
                Signal.of(prefix + "_count", new LogicVector(32)),
                Signal.of(prefix + "_size", new LogicVector(32)),
                Signal.of(prefix + "_peek", type));


    }

    public LogicVector getType() {
        return type;
    }
    public WriterIF getWriter() {
        return writer;
    }
    public ReaderIF getReader() {
        return reader;
    }
    public AuxiliaryIF getAuxiliary() {
        return auxiliary;
    }
    public int getDepth() {
        return depth;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public Stream<PortIF> stream() {
        return Stream.of(
                writer.stream(), reader.stream(), auxiliary.stream()
        ).flatMap(s -> s);

    }

    public Stream<PortIF> streamUnique() {
        if (isInput) {
            return Stream.of(
                    reader.stream(), Stream.of(auxiliary.getPeek())
            ).flatMap(i -> i);

        } else if (isOutput){
            return Stream.of(
                    writer.stream(), Stream.of(auxiliary.capacity)
            ).flatMap(i -> i);
        } else {
            return Stream.of(
                    writer.stream(), reader.stream(), auxiliary.stream()
            ).flatMap(s -> s);
        }

    }
}