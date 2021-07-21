package ch.epfl.vlsc.wsim.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

public abstract class ObjectTypeCpp extends CppType {

    /**
     * Constructs a node with the same line and column numbers as {@code original}.
     *
     * @param original the original node
     * @param isExternal is the ObjectType implemented by an external library
     * @param
     */
    public ObjectTypeCpp(IRNode original, boolean isPointer) {
        super(original, isPointer);
    }

}
