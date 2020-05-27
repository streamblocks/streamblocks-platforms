package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.network.Connection;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class Queue implements SCIF{




    public static class WriterIF implements SCIF{
        public final PortIF din;
        public final PortIF write;
        public final PortIF fullN;
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
    }
    public static class ReaderIF implements SCIF {
        public final PortIF dout;
        public final PortIF read;
        public final PortIF emptyN;
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
    }
    public static class AuxiliaryIF implements SCIF{
        public final PortIF count;
        public final PortIF capacity;
        public final PortIF peek;
        public AuxiliaryIF(Signal count, Signal capacity, Signal peek) {
            this.count = PortIF.of("count", count);
            this.capacity = PortIF.of("size", capacity);
            this.peek = PortIF.of("peek", peek);
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
    }
    private final String name;
    private final LogicVector type;
    private final WriterIF writer;
    private final ReaderIF reader;
    private final AuxiliaryIF auxiliary;
    private final int depth;
    public Queue(Connection connection, int width, int depth) {

        Connection.End source = connection.getSource();
        Connection.End target = connection.getTarget();
        String src = source.getInstance().orElse("") + "_" + source.getPort();
        String tgt = target.getInstance().orElse("") + "_" + target.getPort();
        this.depth = depth;
        name = "q_" + src + "_" + tgt;
        type = new LogicVector(width);
        writer = new WriterIF(
                Signal.of(name + "_din", type),
                name + "_write",
                name + "_full_n");
        reader = new ReaderIF(
                Signal.of(name + "_dout", type),
                name + "_read",
                name + "_empty_n");

        auxiliary = new AuxiliaryIF(
                Signal.of(name + "_count", new LogicVector(32)),
                Signal.of(name + "_size", new LogicVector(32)),
                Signal.of(name + "_peek", type));

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
}