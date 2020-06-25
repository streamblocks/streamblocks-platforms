package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;

import java.util.stream.Stream;

public class SCOutputStage implements SCIF {

    public static class OutputIF implements SCIF {
        private final Queue.ReaderIF reader;
        private final Queue.AuxiliaryIF aux;
        private final PortDecl port;

        public OutputIF(Queue queue, PortDecl port) {
            this.reader = queue.getReader();
            this.aux = queue.getAuxiliary();
            this.port = port;
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
    }

    private final APControl apControl;
    private final OutputIF output;
    private final PortIF init;
    private final String name;

    public SCOutputStage(String name, PortIF init, OutputIF output) {
        this.name = name;
        this.init = init;
        this.output = output;
        this.apControl = new APControl(name + "_");
    }

    public APControl getApControl() { return apControl; }
    public OutputIF getOutput() { return output; }
    public PortIF getInit() { return init; }
    public String getName() {
        return "OutputStage<" + output.getReader().getDout().getSignal().getType() + ">";
    }
    public String getInstanceName() {
        return name;
    }

    public Stream<PortIF> streamUnique() {
        return Stream.of(
                apControl.getDone(),
                apControl.getReady(),
                apControl.getIdle(),
                apControl.getStart()
        );
    }

    public Stream<PortIF> stream() {
        return Stream.concat(output.stream(),
                Stream.concat(
                        Stream.of(apControl.getClock(), apControl.getReset()),
                        streamUnique()));

    }

}
