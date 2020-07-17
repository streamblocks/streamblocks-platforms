package ch.epfl.vlsc.hls.backend.systemc;

import java.util.Optional;
import java.util.stream.Stream;

public class SCTrigger implements SCIF {


    private final String name;

    private final String actorName;

    private final APControl apControl;

    private final SCNetwork.SyncIF globalSync;

    private final PortIF sleep;

    private final PortIF syncExec;
    private final PortIF syncWait;
    private final PortIF waited;
    private final PortIF allWaited;

    private final PortIF actorReturn;
    private final PortIF actorDone;
    private final PortIF actorReady;
    private final PortIF actorIdle;
    private final PortIF actorStart;

    private final int numActions;

    public SCTrigger(SCInstanceIF instance, SCNetwork.SyncIF globalSync, PortIF start) {

        this.apControl = new APControl(instance.getInstanceName() + "_trigger_").withStart(start);
        this.globalSync = globalSync;
        String namePrefix = instance.getInstanceName() + "_";
        this.name = namePrefix + "trigger";
        this.actorName = instance.getOriginalName();
        this.sleep =
                PortIF.of(
                        "sleep",
                        Signal.of(namePrefix + "sleep", new LogicValue()),
                        Optional.of(PortIF.Kind.OUTPUT));
        this.syncExec =
                PortIF.of(
                        "sync_exec",
                        Signal.of(namePrefix + "sync_exec", new LogicValue()),
                        Optional.of(PortIF.Kind.OUTPUT));
        this.syncWait =
                PortIF.of(
                        "sync_wait",
                        Signal.of(namePrefix + "sync_wait", new LogicValue()),
                        Optional.of(PortIF.Kind.OUTPUT));
        this.waited =
                PortIF.of(
                        "waited",
                        Signal.of(namePrefix + "waited", new LogicValue()),
                        Optional.of(PortIF.Kind.OUTPUT));
        this.allWaited =
                PortIF.of(
                        "all_waited",
                        Signal.of(namePrefix + "all_waited", new LogicValue()),
                        Optional.of(PortIF.Kind.INPUT));
        this.actorDone =
                PortIF.of(
                        "actor_done",
                        instance.getApControl().getDoneSignal(),
                        Optional.of(PortIF.Kind.INPUT));
        this.actorIdle =
                PortIF.of(
                        "actor_idle",
                        instance.getApControl().getIdleSignal(),
                        Optional.of(PortIF.Kind.INPUT));
        this.actorReady =
                PortIF.of(
                        "actor_ready",
                        instance.getApControl().getReadySignal(),
                        Optional.of(PortIF.Kind.INPUT));
        this.actorStart =
                PortIF.of(
                        "actor_start",
                        instance.getApControl().getStartSignal(),
                        Optional.of(PortIF.Kind.INPUT));
        this.actorReturn =
                PortIF.of(
                        "actor_return",
                        instance.getReturn().getSignal().withRange(1, 0),
                        Optional.of(PortIF.Kind.INPUT));
        this.numActions = instance.getNumActions();

    }

    public PortIF getSyncExec() {
        return syncExec;
    }

    public PortIF getSyncWait() {
        return syncWait;
    }

    public PortIF getWaited() {
        return waited;
    }

    public PortIF getAllWaited() {
        return allWaited;
    }

    public PortIF getActorReturn() {
        return actorReturn;
    }

    public PortIF getActorDone() {
        return actorDone;
    }

    public PortIF getActorReady() {
        return actorReady;
    }

    public PortIF getActorIdle() {
        return actorIdle;
    }

    public PortIF getActorStart() {
        return actorStart;
    }

    public PortIF getSleep() {
        return sleep;
    }

    public APControl getApControl() {
        return apControl;
    }

    public int getNumActions() {
        return numActions;
    }

    public String getActorName() { return actorName; }
    @Override
    public Stream<PortIF> stream() {

        return Stream.concat(
                apControl.stream(),
                Stream.concat(
                        globalSync.stream(),
                        Stream.concat(
                                this.streamUniqueSync(),
                                Stream.of(
                                        actorReturn,
                                        actorDone,
                                        actorReady,
                                        actorIdle,
                                        actorStart))));

    }

    public Stream<PortIF> streamUnique() {
        return
                Stream.concat(
                        streamUniqueAP(),
                        streamUniqueSync());
    }

    public Stream<PortIF> streamUniqueSync() {
        return Stream.of(
                sleep,
                syncExec,
                syncWait,
                waited,
                allWaited);
    }

    public Stream<PortIF> streamUniqueAP() {
        return Stream.of(
                apControl.getDone(),
                apControl.getIdle(),
                apControl.getReady()
        );
    }

    public String getName() {
        return name;
    }
}
