package ch.epfl.vlsc.hls.backend.systemc;

import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

public class LogicVector implements SCType{

    private final int width;

    public LogicVector(int width) {
        this.width = width;
    }

    public int getWidth() {
        return this.width;
    }

    @Override
    public String getType() {
        if (width <= 32) {
            return "uint32_t";
        } else if (width <= 64) {
            return "vluint64_t";
        } else {
            return "sc_bv<" + width + ">";
        }

    }
}
