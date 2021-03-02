package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;

import java.util.Optional;

import java.util.stream.Stream;

abstract class SCIOStage implements SCInputOutputIF {

    public static class KernelArgIF implements SCIF {
        private final PortIF data_buffer;
        private final PortIF meta_buffer;
        private final PortIF alloc_size;
        private final PortIF head;
        private final PortIF tail;

        public KernelArgIF(String instanceName) {
            this.data_buffer = PortIF.of(
                    "data_buffer",
                    Signal.of(instanceName + "_data_buffer", new LogicVector(64)),
                    Optional.of(PortIF.Kind.INPUT));
            this.meta_buffer = PortIF.of(
                    "meta_buffer",
                    Signal.of(instanceName + "_meta_buffer", new LogicVector(64)),
                    Optional.of(PortIF.Kind.INPUT));
            this.alloc_size = PortIF.of(
                    "alloc_size",
                    Signal.of(instanceName + "_alloc_size", new LogicVector(32)),
                    Optional.of(PortIF.Kind.INPUT));
            this.head = PortIF.of(
                    "head",
                    Signal.of(instanceName + "_head", new LogicVector(32)),
                    Optional.of(PortIF.Kind.INPUT));
            this.tail = PortIF.of(
                    "tail",
                    Signal.of(instanceName + "_tail", new LogicVector(32)),
                    Optional.of(PortIF.Kind.INPUT));


        }

        @Override
        public Stream<PortIF> stream() {
            return Stream.of(
                    data_buffer, meta_buffer, alloc_size, head, tail
            );
        }
    }

    // ap control interface
    protected final APControl apControl;
    // kernel start port interface
    protected final PortIF kernelStart;
    // return value port interface
    protected final PortIF ret;
    // interfaces set by the kernel arguments
    protected final KernelArgIF args;
    // IO stage instance name
    protected final String instanceName;

    public SCIOStage(String instanceName, PortIF kernelStart) {
        this.instanceName = instanceName;
        this.kernelStart = kernelStart;
        this.apControl = new APControl(instanceName + "_");
        this.ret = PortIF.of(
                "ap_return",
                Signal.of(instanceName + "_ap_return", new LogicVector(32)),
                Optional.of(PortIF.Kind.OUTPUT)
        );
        this.args = new KernelArgIF(instanceName);

    }

    @Override
    public Stream<PortIF> streamUnique() {
        return Stream.of(
                        apControl.getDone(),
                        apControl.getReady(),
                        apControl.getIdle(),
                        apControl.getStart(),
                        ret);
    }

    @Override
    public Stream<PortIF> stream() {
        return Stream.concat(
            Stream.of(apControl.getClock(), apControl.getReset(), kernelStart),
                Stream.concat(streamUnique(), args.stream())
        );
    }

    public PortIF getKernelStart() { return kernelStart; }


    public String getInstanceName() {
        return instanceName;
    }

    @Override
    public PortIF getReturn() {
        return ret;
    }

    @Override
    public int getNumActions() {
        return 1;
    }

    @Override
    public APControl getApControl() { return apControl; }

    public Stream<PortIF> getKernelArgs() {
        return args.stream();
    }

    public abstract int getDepth();

}
