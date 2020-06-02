package ch.epfl.vlsc.hls.backend.systemc;

public class LogicValue implements SCType {

    public enum Value {
        SC_LOGIC_0,
        SC_LOGIC_1;
        @Override
        public String toString() {
            String ret;
            switch (this) {
                case SC_LOGIC_0:
                    return "false";
                case SC_LOGIC_1:
                    return "true";
                default:
                    return "ERROR";
            }
        }
    };
    @Override
    public String getType() {
        return "bool";
    }

}
