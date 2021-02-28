package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;

import java.util.Optional;
import java.util.stream.Stream;

public class SCInputStage extends SCIOStage {

    public static class InputIF implements SCIF {
        private final Queue.WriterIF writer;
        private final Queue.AuxiliaryIF aux;
        private final PortDecl port;
        private int depth;
        public InputIF(Queue queue, PortDecl port) {
            this.writer = queue.getWriter().withPrefix("fifo_");
            this.aux = queue.getAuxiliary().withPrefix("fifo_");
            this.port = port;
            this.depth = queue.getDepth();
        }


        @Override
        public Stream<PortIF> stream() {
            return Stream.concat(this.writer.stream(), Stream.of(aux.getCount(), aux.getCapacity()));
        }

        public PortDecl getPort() {
            return port;
        }

        public Queue.WriterIF getWriter() {
            return writer;
        }

        public Queue.AuxiliaryIF getAuxiliary() {
            return aux;
        }
        public int getDepth() { return depth; }
    }

    private final InputIF input;


    public SCInputStage(String instanceName, PortIF kernelStart, InputIF input) {
        super(instanceName, kernelStart);
        this.input = input;
    }


    public InputIF getInput() {
        return input;
    }

    @Override
    public String getName() {
        return "InputMemoryStage<" + input.writer.getDin().getSignal().getType() +
                ", " + getDepth() + ">";
    }

    public Stream<PortIF> streamUnique() {
        return Stream.concat(super.streamUnique(),
                input.stream()
        );
    }

    public Stream<PortIF> stream() {
        return
                Stream.concat(
                    super.stream(),
                    streamUnique());

    }

    public PortDecl getPort() {
        return input.getPort();
    }

    public String getType() { return input.writer.getDin().getSignal().getType(); }

    @Override
    public int getDepth() {
        return input.getDepth();
    }
}
