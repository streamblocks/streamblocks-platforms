package ch.epfl.vlsc.hls.backend.systemc;



import java.util.Optional;

public class Signal {

    private final String name;
    private final SCType type;
    public static class Range{
        private int high;
        private int low;
        Range(int high, int low) {
            this.high = high;
            this.low = low;
        }
        int getHigh() {
            return high;
        }
        int getLow() {
            return low;
        }
    }
    private final Optional<Range> range;

    public Signal(String name, SCType type) {
        this.name = name;
        this.type = type;
        this.range = Optional.empty();
    }
    public Signal(String name, SCType type, Range range) {
        this.name = name;
        this.type = type;
        this.range = Optional.of(range);
    }
    public String getName() {
        return this.name;
    }
    public String getNameRanged() {
        if (range.isPresent()) {
            return String.format("%s.range(%d, %d)", this.name, this.range.get().getHigh(), this.range.get().getLow());
        } else {
            return this.name;
        }
    }

    public Optional<Range> getRange(){
        return range;
    }

    public String getType() { return this.type.getType(); }
    public Signal withRange(int high, int low) {
        return new Signal(this.name, this.type, new Range(high, low));
    }

    public static Signal of(String name, SCType type) {
        return new Signal(name, type);
    }


}
