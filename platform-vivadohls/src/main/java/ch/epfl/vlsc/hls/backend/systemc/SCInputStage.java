package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;

import java.util.Optional;
import java.util.stream.Stream;

public class SCInputStage implements SCInstanceIF {

    public static class InputIF implements SCIF {
        private final Queue.WriterIF writer;
        private final Queue.AuxiliaryIF aux;
        private final PortDecl port;

        public InputIF(Queue queue, PortDecl port) {
            this.writer = queue.getWriter();
            this.aux = queue.getAuxiliary();
            this.port = port;
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
    }
    private final APControl apControl;
    private final InputIF input;
    private final PortIF init;
    private final String instanceName;
    private final PortIF ret;

    public SCInputStage(String instanceName, PortIF init, InputIF input) {
        this.instanceName = instanceName;
        this.init = init;
        this.input = input;
        this.apControl = new APControl(instanceName + "_");
        this.ret = PortIF.of(
                "ap_return",
                Signal.of(instanceName + "_", new LogicVector(32)),
                Optional.of(PortIF.Kind.OUTPUT));
    }

    @Override
    public APControl getApControl() {
        return apControl;
    }

    @Override
    public int getNumActions() {
        return 1;
    }

    public InputIF getInput() {
        return input;
    }
    public PortIF getInit() {
        return init;

    }

    @Override
    public String getInstanceName() {
        return instanceName;
    }

    @Override
    public PortIF getReturn() {
        return ret;
    }

    @Override
    public String getName() {
        return "InputStage<" + input.writer.getDin().getSignal().getType() + ">";
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
        return Stream.concat(input.stream(),
                Stream.concat(
                    Stream.of(apControl.getClock(), apControl.getReset()),
                    streamUnique()));

    }


}
