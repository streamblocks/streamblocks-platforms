package ch.epfl.vlsc.hls.backend.systemc;


public interface SCInstanceIF extends SCIF {

    APControl getApControl();
    int getNumActions();
    String getName();
    String getInstanceName();
    PortIF getReturn();
}
