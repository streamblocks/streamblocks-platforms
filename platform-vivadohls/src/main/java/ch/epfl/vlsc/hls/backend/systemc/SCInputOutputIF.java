package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.ir.entity.PortDecl;

public interface SCInputOutputIF extends SCInstanceIF {

    PortDecl getPort();
    default String getOriginalName() { return getName(); }
    String getType();
    String getUnderlyingType();

    @Override
    default Boolean getPipelined() { return false; }
}
