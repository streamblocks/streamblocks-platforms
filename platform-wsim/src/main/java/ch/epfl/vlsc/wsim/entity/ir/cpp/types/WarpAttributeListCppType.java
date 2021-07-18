package ch.epfl.vlsc.wsim.entity.ir.cpp.types;


import se.lth.cs.tycho.ir.IRNode;

import java.util.function.Consumer;

public class WarpAttributeListCppType extends LibraryObjectCppType {


    public WarpAttributeListCppType(WarpAttributeListCppType original, boolean isPointer) {
        super(original, isPointer);
    }
    public WarpAttributeListCppType(boolean isPointer) {
        this(null, isPointer);
    }

    public WarpAttributeListCppType() {
        this(null, false);
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {

    }

    @Override
    public WarpAttributeListCppType transformChildren(Transformation transformation) {
        return null;
    }

    @Override
    public String toString() {
        return "::wsim::AttributeList";
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof VectorCppType)) {
            return false;
        } else {
            WarpAttributeListCppType thatCasted = (WarpAttributeListCppType) that;
            return this.isPointer() == thatCasted.isPointer();
        }

    }
}
