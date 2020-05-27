package ch.epfl.vlsc.hls.backend.systemc;


import javax.sound.sampled.Port;
import java.util.Optional;
import java.util.stream.Stream;

public class APControl implements SCIF {

    private final PortIF clk;
    private final PortIF rstN;
    public final PortIF start;
    private final PortIF done;
    public final PortIF idle;
    private final PortIF ready;
//    private final PortIF ret;

    public APControl() {
        this("");
    }

    public APControl(String prefix_) {

        clk = PortIF.of(
                "ap_clk",
                Signal.of("ap_clk", new LogicValue()),
                Optional.of(PortIF.Kind.INPUT));

        rstN = PortIF.of(
                "ap_rst_n",
                Signal.of("rst_n", new LogicValue()),
                Optional.of(PortIF.Kind.INPUT));
        start = PortIF.of(
                "ap_start",
                Signal.of(prefix_ + "start", new LogicValue()),
                Optional.of(PortIF.Kind.INPUT));
        done = PortIF.of(
                "ap_done",
                Signal.of(prefix_ + "done", new LogicValue()),
                Optional.of(PortIF.Kind.OUTPUT));
        idle = PortIF.of(
                "ap_idle",
                Signal.of(prefix_ + "idle", new LogicValue()),
                Optional.of(PortIF.Kind.OUTPUT));
        ready = PortIF.of(
                "ap_ready",
                Signal.of(prefix_ + "ready", new LogicValue()),
                Optional.of(PortIF.Kind.OUTPUT));
//        ret = PortIF.of(
//                "ap_return",
//                Signal.of(prefix_ + "return", new LogicVector(32)),
//                Optional.of(PortIF.Kind.OUTPUT));
    }

    public APControl(PortIF clk, PortIF rstN, PortIF start, PortIF done, PortIF idle, PortIF ready) {
        this.clk = clk;
        this.rstN = rstN;
        this.start = start;
        this.done = done;
        this.idle = idle;
        this.ready = ready;
//        this.ret = ret;
    }

    public static APControl of(String prefix) {
        return new APControl(prefix);
    }

    public APControl withStart(PortIF start) {
        return new APControl(this.clk, this.rstN, start, this.done, this.idle, this.ready);
    }



    public Signal getClockSignal() {
        return this.clk.getSignal();
    }

    public Signal getResetSignal() {
        return this.rstN.getSignal();
    }

    public Signal getDoneSignal() {
        return this.done.getSignal();
    }

    public Signal getReadySignal() {
        return this.ready.getSignal();
    }

    public Signal getIdleSignal() {
        return this.idle.getSignal();
    }

    public Signal getStartSignal() {
        return this.start.getSignal();
    }

    public PortIF getStart() {
        return this.start;
    }

    public PortIF getClock() {
        return this.clk;
    }

    public PortIF getReset() {
        return this.rstN;
    }

    public PortIF getDone() {
        return this.done;
    }

    public PortIF getIdle() {
        return this.idle;
    }

    public PortIF getReady() {
        return this.ready;
    }

    @Override
    public Stream<PortIF> stream() {
        return Stream.of(clk, rstN, start, done, idle, ready);
    }


}
