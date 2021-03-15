package ch.epfl.vlsc.hls.backend.directives;

public enum Directives {

    NULL("null"),
    INLINE("inline"),
    LATENCY("latency"),
    LOOP_FLATTEN("loop_flatten"),
    LOOP_MERGE("loop_merge"),
    LOOP_TRIPCOUNT("loop_tripcount"),
    PIPELINE("pipeline"),
    ARRAY_PARTITION("array_partition"),
    UNROLL("unroll");

    private String name;

    Directives(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static Directives directive(String name) {
        switch (name) {
            case "array_partition":
                return ARRAY_PARTITION;
            case "inline":
                return INLINE;
            case "latency":
                return LATENCY;
            case "loop_flatten":
                return LOOP_FLATTEN;
            case "loop_merge":
                return LOOP_MERGE;
            case "loop_tripcount":
                return LOOP_TRIPCOUNT;
            case "pipeline":
                return PIPELINE;
            case "unroll":
                return UNROLL;
            default:
                return NULL;
        }
    }

}
