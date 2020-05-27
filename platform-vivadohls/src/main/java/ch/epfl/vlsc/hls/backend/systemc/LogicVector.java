package ch.epfl.vlsc.hls.backend.systemc;

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
        return "sc_lv<" + this.width + ">";
    }
}
