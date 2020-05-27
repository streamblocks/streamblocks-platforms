package ch.epfl.vlsc.hls.backend.systemc;

public class LogicValue implements SCType {

    public enum Value {
        SC_LOGIC_0,
        SC_LOGIC_1
    };
    @Override
    public String getType() {
        return "sc_logic";
    }

}
