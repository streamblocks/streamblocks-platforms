package ch.epfl.vlsc.wsim.entity.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

import java.util.Objects;
import java.util.function.Consumer;

public class StringCppType extends LibraryObjectCppType {

    public StringCppType(StringCppType original, boolean isPointer) {
        super(original, isPointer);
    }
    public StringCppType(boolean isPointer) {
        this(null, isPointer);
    }
    public StringCppType() {
        this(null, false);
    }

    @Override
    public String toString() {
        return "::std::string";
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof  StringCppType)) {
            return false;
        } else {
            StringCppType castedType = (StringCppType) that;
            return this.isPointer() == castedType.isPointer();
        }
    }

    @Override
    public StringCppType transformChildren(Transformation transformation) {
        return this;
    }
    @Override
    public void forEachChild(Consumer<? super IRNode> action) {}
}
