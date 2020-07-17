package ch.epfl.vlsc.hls.backend.systemc;


import java.util.stream.Stream;

public interface SCInstanceIF extends SCIF {

    APControl getApControl();
    int getNumActions();
    String getName();
    String getInstanceName();
    String getOriginalName();
    PortIF getReturn();
    Stream<PortIF> streamUnique();
}
