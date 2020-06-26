package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class SCOutputStage implements SCInstanceIF {

    public static class OutputIF implements SCIF {
        private final Queue.ReaderIF reader;
        private final Queue.AuxiliaryIF aux;
        private final PortDecl port;

        public OutputIF(Queue queue, PortDecl port) {
            this.reader = queue.getReader().withPrefix("fifo_");
            this.aux = queue.getAuxiliary().withPrefix("fifo_");
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
    private final String instanceName;
    private final PortIF ret;

    public SCOutputStage(String instanceName, PortIF init, OutputIF output) {
        this.instanceName = instanceName;
        this.init = init;
        this.output = output;
        this.apControl = new APControl(instanceName + "_");
        this.ret = PortIF.of(
                "ap_return",
                Signal.of(instanceName + "_ap_return", new LogicVector(32)),
                Optional.of(PortIF.Kind.OUTPUT));
    }

    public APControl getApControl() { return apControl; }

    @Override
    public int getNumActions() {
        return 1;
    }

    public OutputIF getOutput() { return output; }
    public PortIF getInit() { return init; }
    public String getName() {
        return "iostage::OutputStage<" + output.getReader().getDout().getSignal().getType() + ">";
    }
    public String getInstanceName() {
        return instanceName;
    }

    @Override
    public PortIF getReturn() {
        return ret;
    }

    public Stream<PortIF> streamUnique() {
        return Stream.concat(
                Stream.of(apControl.getDone(),
                apControl.getReady(),
                apControl.getIdle(),
                apControl.getStart(),
                ret),
                output.stream()
        );
    }

    public Stream<PortIF> stream() {
        return
                Stream.concat(
                        Stream.of(apControl.getClock(), apControl.getReset(), init),
                        streamUnique());

    }

    public PortDecl getPort() {
        return output.getPort();
    }

}
