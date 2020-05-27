package ch.epfl.vlsc.hls.backend.systemc;



import java.util.Optional;

public class PortIF {

    public enum Kind { INPUT, OUTPUT }
    private final String formalName;
    private final Signal signal;
    private final Optional<Kind> kind;
    public PortIF(String formalName, Signal signal) {
        this(formalName, signal, Optional.empty());
    }
    public PortIF(String formalName, Signal signal, Optional<Kind> kind) {
        this.formalName = formalName;
        this.signal = signal;
        this.kind = kind;
    }

    public static PortIF of(String formalName, Signal signal) {
        return of(formalName, signal, Optional.empty());
    }
    public static PortIF of(String formalName, Signal signal, Optional<Kind> kind) {
        return new PortIF(formalName, signal, kind);
    }

    public PortIF withKind(Kind kind) {
        return new PortIF(this.formalName, this.signal, Optional.of(kind));
    }
    public Signal getSignal() {
        return signal;
    }

    public String getName() {
        return formalName;
    }

    public Optional<Kind> getKind() {
        return kind;
    }
}
