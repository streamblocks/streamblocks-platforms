package ch.epfl.vlsc.wsim.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

import java.util.function.Consumer;

public class WarpActorStateCppType extends LibraryObjectCppType {

    public static String getPcFieldName() { return "pc"; }
    public static String getLvtFieldName() { return "lvt"; }
    public WarpActorStateCppType(WarpActorStateCppType original, boolean isPointer) {
        super(original, isPointer);
    }

    public WarpActorStateCppType(boolean isPointer) {
        this(null, isPointer);
    }

    public WarpActorStateCppType() {
        this(null, false);
    }

    @Override
    public String toString() {
        return "::wsim::ActorState";
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {

    }
    @Override
    public WarpActorStateCppType transformChildren(Transformation transformation) {
        return this;
    }
}
