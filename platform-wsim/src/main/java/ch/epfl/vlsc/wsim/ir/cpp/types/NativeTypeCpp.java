package ch.epfl.vlsc.wsim.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

import java.util.function.Consumer;

/**
 * Base interface for native C++ types, e.g., int, double, etc.
 */
public final class NativeTypeCpp extends CppType {




    public enum Kind {
        UINT8 ("uint8_t"),
        INT8 ("int8_t"),
        UINT16 ("uint16_t"),
        INT16 ("int16_t"),
        UINT32 ("uint32_t"),
        INT32 ("int32_t"),
        UINT64 ("uint64_t"),
        INT64 ("int64_t"), FLOAT ("float"),
        DOUBLE ("double"), CHAR ("char"),
        SIZE_T ("size_t"), BOOL ("bool"), VOID ("void"),
        VIRTUAL_TIME("::wsim::VirtualTime::time");
        private final String name;
        Kind(String name) { this.name = name;}
        @Override
        public String toString() {
            return this.name;
        }
    }
    private final Kind kind;



    public NativeTypeCpp(CppType original, Kind kind, boolean isPointer) {
        super(original, isPointer);
        this.kind = kind;
    }

    public NativeTypeCpp(Kind kind) {
        this(null, kind, false);
    }
    public NativeTypeCpp(Kind kind, boolean isPointer) {
        this(null, kind, isPointer);
    }

    private NativeTypeCpp copy(Kind kind, boolean isPointer) {
        if (this.kind == kind && this.isPointer() == isPointer)
            return this;
        else
            return new NativeTypeCpp(kind, isPointer);
    }

    @Override
    public NativeTypeCpp transformChildren(Transformation transformation) {
        return this;
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        // do nothing
    }

    public NativeTypeCpp withKind(Kind kind) {
        return copy(kind, isPointer());
    }


    public NativeTypeCpp withPointer(boolean isPointer) {
        return copy(this.kind, isPointer);
    }

    public Kind getKind() {
        return this.kind;
    }


    public static NativeTypeCpp Int8(boolean isSigned) {
        if (isSigned)
            return new NativeTypeCpp(Kind.INT8);
        else
            return new NativeTypeCpp(Kind.UINT8);
    }
    public static NativeTypeCpp Int16(boolean isSigned) {
        if (isSigned)
            return new NativeTypeCpp(Kind.INT16);
        else
            return new NativeTypeCpp(Kind.UINT16);
    }

    public static NativeTypeCpp Int32(boolean isSigned) {
        if (isSigned)
            return new NativeTypeCpp(Kind.INT32);
        else
            return new NativeTypeCpp(Kind.UINT32);
    }

    public static NativeTypeCpp Int64(boolean isSigned) {
        if (isSigned)
            return new NativeTypeCpp(Kind.INT64);
        else
            return new NativeTypeCpp(Kind.UINT64);
    }

    public static NativeTypeCpp Char() {
        return new NativeTypeCpp(Kind.CHAR);
    }
    public static NativeTypeCpp SizeT() {
        return new NativeTypeCpp(Kind.SIZE_T);
    }
    public static NativeTypeCpp Double() {
        return new NativeTypeCpp(Kind.DOUBLE);
    }
    public static NativeTypeCpp Float() {
        return new NativeTypeCpp(Kind.FLOAT);
    }
    public static NativeTypeCpp Bool() {
        return new NativeTypeCpp(Kind.BOOL);
    }
    public static NativeTypeCpp Void() {
        return new NativeTypeCpp(Kind.VOID);
    }
    public static NativeTypeCpp VirtualTime() { return new NativeTypeCpp(Kind.VIRTUAL_TIME); }
    @Override
    public String toString() { return this.kind.toString(); }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof  NativeTypeCpp)) {
            return false;
        } else {
            NativeTypeCpp castedType = (NativeTypeCpp) that;
            return this.isPointer() == castedType.isPointer() && this.kind.equals(castedType.kind);
        }
    }
}
