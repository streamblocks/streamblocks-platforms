package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;


import java.util.Map;
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
//    private final ImmutableList<InputIF> writers;
//    private final ImmutableList<OutputIF> readers;
    private final APControl apControl;
    // Actors
    private final ImmutableList<SCInstance> instances;
    private final ImmutableList<SCInputStage> inputStages;
    private final ImmutableList<SCOutputStage> outputStages;
    // Triggers
    private final ImmutableList<SCTrigger> instanceTriggers;
    private final ImmutableList<SCTrigger> inputStageTriggers;
    private final ImmutableList<SCTrigger> outputStageTriggers;
    private final ImmutableList<Queue> queues;
    private final SyncIF globalSync;
    private final ImmutableList<Signal> instancesSync;
    private final ImmutableList<Signal> inputStagesSync;
    private final ImmutableList<Signal> outputStagesSync;
    private final Signal amIdle;
    private final Signal amIdleReg;
    private final PortIF init;


    public SCNetwork(String identifier, ImmutableList<SCInputStage> inputStages,
                     ImmutableList<SCOutputStage> outputStages, ImmutableList<SCInstance> instances,
                     ImmutableList<Queue> queues, PortIF init) {
        this.identifier = identifier;
        this.inputStages = inputStages;
        this.outputStages = outputStages;
        this.apControl = new APControl();
        this.instances = instances;
        this.queues = queues;
        this.globalSync = new SyncIF();

        // create trigger
        this.instanceTriggers = instances.map(this::makeTrigger);
        this.inputStageTriggers = inputStages.map(this::makeTrigger);
        this.outputStageTriggers = outputStages.map(this::makeTrigger);

        // create sync signals
        this.instancesSync = instances.map(this::makeSyncSignal);
        this.inputStagesSync = inputStages.map(this::makeSyncSignal);
        this.outputStagesSync = outputStages.map(this::makeSyncSignal);

        // internal signals
        this.amIdle = Signal.of("am_idle", new LogicValue());
        this.amIdleReg = Signal.of("am_idle_r", new LogicValue());

        this.init = init;
    }

    private SCTrigger makeTrigger(SCInstanceIF inst) {
        return new SCTrigger(inst, this.globalSync, this.apControl.getStart());
    }
    private Signal makeSyncSignal(SCInstanceIF inst) {
        return Signal.of(inst.getInstanceName() + "_sync", new LogicValue());
    }
    public String getIdentifier() {
        return identifier;
    }

    public ImmutableList<SCInputStage> getInputStages() {
        return inputStages;
    }

    public ImmutableList<SCOutputStage> getOutputStages() {
        return outputStages;
    }

    public ImmutableList<SCInstance> getInstances() {
        return instances;
    }

    public ImmutableList<Queue> getQueues() {
        return queues;
    }

    public ImmutableList<SCTrigger> getInstanceTriggers() {
        return instanceTriggers;
    }

    public ImmutableList<SCTrigger> getInputStageTriggers() { return inputStageTriggers; }

    public ImmutableList<SCTrigger> getOutputStageTriggers() { return outputStageTriggers; }

    @Override
    public Stream<PortIF> stream() {
        return Stream.concat(Stream.of(init), apControl.stream());

    }

    public Stream<Signal> getInternalSignals() {
        return Stream.concat(
                this.globalSync.stream().map(PortIF::getSignal),
                Stream.concat(
                        getAllSyncSignals().stream(),
                        Stream.of(
                                this.amIdle,
                                this.amIdleReg
                        )));
    }



    public SyncIF getGlobalSync() {
        return globalSync;
    }

    public APControl getApControl() {
        return apControl;
    }

    public PortIF getInit() { return init; }

    public Signal getAmIdle() {
        return amIdle;
    }

    public Signal getAmIdleReg() {
        return amIdleReg;
    }

    public SCTrigger getInstanceTrigger(SCInstance instance) {
        return instanceTriggers.get(instances.indexOf(instance));
    }


    public ImmutableList<SCTrigger> getAllTriggers() {
        ImmutableList.Builder<SCTrigger> triggers = ImmutableList.builder();
        triggers.addAll(instanceTriggers)
                .addAll(inputStageTriggers)
                .addAll(outputStageTriggers);
        return triggers.build();
    }

    public ImmutableList<Signal> getAllSyncSignals() {
        ImmutableList.Builder<Signal> syncs = ImmutableList.builder();
        syncs.addAll(instancesSync).addAll(inputStagesSync).addAll(outputStagesSync);
        return syncs.build();
    }

    public Signal getSyncSignal(SCTrigger trigger) {
        return getAllSyncSignals().get(getAllTriggers().indexOf(trigger));
    }
}
