package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class SCOutputStage extends SCIOStage {

    public static class OutputIF implements SCIF {
        private final Queue.ReaderIF reader;
        private final Queue.AuxiliaryIF aux;
        private final PortDecl port;
        private final int depth;
        public OutputIF(Queue queue, PortDecl port) {
            this.reader = queue.getReader().withPrefix("fifo_");
            this.aux = queue.getAuxiliary().withPrefix("fifo_");
            this.port = port;
            this.depth = queue.getDepth();
        }


        @Override
        public Stream<PortIF> stream() {
            return Stream.concat(this.reader.stream(), Stream.of(aux.getCount(), aux.getPeek()));
        }

        public PortDecl getPort() {
            return port;
        }

        public Queue.ReaderIF getReader() {
            return reader;
        }

        public Queue.AuxiliaryIF getAuxiliary() {
            return aux;
        }

        public int getDepth() {
            return depth;
        }
    }

    private final OutputIF output;
    public SCOutputStage(String instanceName, PortIF kernelStart, OutputIF output, String underlyingType) {
        super(instanceName, kernelStart, underlyingType);
        this.output = output;

    }


    public OutputIF getOutput() { return output; }

    @Override
    public String getName() {
        return "SimulatedOutputMemoryStage<" +
                String.join(", ",
                        getType(),
                        getUnderlyingType(),
                        String.valueOf(getDepth())) + ">";
    }


    @Override
    public Stream<PortIF> streamUnique() {
        return Stream.concat(
                super.streamUnique(),
                output.stream()
        );
    }

    @Override
    public PortDecl getPort() {
        return output.getPort();
    }

    @Override
    public String getType() { return output.getReader().getDout().getSignal().getType(); }


    @Override
    public int getDepth() {
        return output.getDepth();
    }
}
