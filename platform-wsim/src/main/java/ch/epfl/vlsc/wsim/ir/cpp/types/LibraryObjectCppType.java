package ch.epfl.vlsc.wsim.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

public abstract class LibraryObjectCppType extends ObjectTypeCpp{


    /**
     * Constructs a node with the same line and column numbers as {@code original}.
     *
     * @param original  the original node
     * @param isPointer is the object type pointer?
     */
    public LibraryObjectCppType(IRNode original, boolean isPointer) {
        super(original, isPointer);
    }
}
