package ch.epfl.vlsc.wsim.entity.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

import java.util.function.Consumer;

public class WarpActorBaseCppType extends LibraryObjectCppType {

    public WarpActorBaseCppType(WarpActorBaseCppType original, boolean isPointer) {
        super(original, isPointer);
    }
    public WarpActorBaseCppType(boolean isPointer) {
        this(null, isPointer);
    }
    public WarpActorBaseCppType() {
        this(null, false);
    }
    @Override
    public String toString() {
        return "::wsim::ActorBase";
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof WarpActorBaseCppType)) {
            return false;
        } else {
            WarpActorBaseCppType thatCasted = (WarpActorBaseCppType) that;
            return this.isPointer() == thatCasted.isPointer();
        }
    }

    @Override
    public void forEachChild(Consumer<? super  IRNode> action) {

    }
    @Override
    public WarpActorBaseCppType transformChildren(Transformation transformation) {
        return this;
    }

}
