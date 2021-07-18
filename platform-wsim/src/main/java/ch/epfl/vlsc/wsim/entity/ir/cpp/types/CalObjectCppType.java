package ch.epfl.vlsc.wsim.entity.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

public abstract class CalObjectCppType extends ObjectTypeCpp {

    /**
     * Constructs a node with the same line and column numbers as {@code original}.
     *
     * @param original  the original node
     * @param isPointer
     */
    public CalObjectCppType(IRNode original, boolean isPointer) {
        super(original, isPointer);
    }
}
