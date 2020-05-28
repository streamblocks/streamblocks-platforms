package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;


import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SCNetwork implements SCIF {


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


    public static class SyncIF implements SCIF {

        private final PortIF externalEnqueue;
        private final PortIF allSync;
        private final PortIF allSyncWait;
        private final PortIF allSleep;

        public SyncIF() {

            this.externalEnqueue =
                    PortIF.of(
                            "external_enqueue",
                            Signal.of("external_enqueue", new LogicValue()),
                            Optional.of(PortIF.Kind.INPUT));
            this.allSync =
                    PortIF.of(
                            "all_sync",
                            Signal.of("all_sync", new LogicValue()),
                            Optional.of(PortIF.Kind.INPUT));
            this.allSyncWait =
                    PortIF.of(
                            "all_sync_wait",
                            Signal.of("all_sync_wait", new LogicValue()),
                            Optional.of(PortIF.Kind.INPUT));
            this.allSleep =
                    PortIF.of(
                            "all_sleep",
                            Signal.of("all_sleep", new LogicValue()),
                            Optional.of(PortIF.Kind.INPUT));
        }

        @Override
        public Stream<PortIF> stream() {
            return Stream.of(externalEnqueue, allSync, allSyncWait, allSleep);
        }

        public PortIF getExternalEnqueue() {
            return this.externalEnqueue;
        }

        public PortIF getAllSleep() {
            return allSleep;
        }

        public PortIF getAllSync() {
            return allSync;
        }

        public PortIF getAllSyncWait() {
            return allSyncWait;
        }
    }

    private final String identifier;
    private final ImmutableList<InputIF> writers;
    private final ImmutableList<OutputIF> readers;
    private final APControl apControl;
    private final ImmutableList<SCInstance> instances;
    private final ImmutableList<SCTrigger> triggers;
    private final ImmutableList<Queue> queues;
    private final SyncIF globalSync;
    private final ImmutableList<Signal> instanceSync;
    private final Signal amIdle;
    private final Signal amIdleReg;

    public SCNetwork(String identifier, ImmutableList<InputIF> writers, ImmutableList<OutputIF> readers,
                     ImmutableList<SCInstance> instances, ImmutableList<Queue> queues) {
        this.identifier = identifier;
        this.writers = writers;
        this.readers = readers;
        this.apControl = new APControl();
        this.instances = instances;
        this.queues = queues;
        this.globalSync = new SyncIF();
        this.triggers = instances.map(inst -> new SCTrigger(inst, this.globalSync, this.apControl.getStart()));
        this.instanceSync = ImmutableList.from(instances.stream().map(
                inst ->
                        Signal.of(inst.getName() + "_sync", new LogicValue())
        ).collect(Collectors.toList()));

        this.amIdle = Signal.of("am_idle", new LogicValue());
        this.amIdleReg = Signal.of("am_idle_r", new LogicValue());
    }

    public String getIdentifier() {
        return identifier;
    }

    public ImmutableList<InputIF> getInputs() {
        return writers;
    }

    public ImmutableList<OutputIF> getOutputs() {
        return readers;
    }

    public ImmutableList<SCInstance> getInstances() {
        return instances;
    }

    public ImmutableList<Queue> getQueues() {
        return queues;
    }

    public ImmutableList<SCTrigger> getTriggers() {
        return triggers;
    }

    @Override
    public Stream<PortIF> stream() {
        return Stream.concat(
                writers.stream().flatMap(InputIF::stream),
                Stream.concat(
                        readers.stream().flatMap(OutputIF::stream),
                        apControl.stream()));

    }

    public Stream<Signal> getInternalSignals() {
        return Stream.concat(
                this.globalSync.stream().map(PortIF::getSignal),
                Stream.concat(
                        this.instanceSync.stream(),
                        Stream.of(
                                this.amIdle,
                                this.amIdleReg
                        )));
    }

    public ImmutableList<Signal> getInstanceSyncSignals() {
        return this.instanceSync;
    }

    public SyncIF getGlobalSync() {
        return globalSync;
    }

    public APControl getApControl() {
        return apControl;
    }

    public Signal getAmIdle() {
        return amIdle;
    }

    public Signal getAmIdleReg() {
        return amIdleReg;
    }
}
