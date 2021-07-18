package ch.epfl.vlsc.wsim.entity.ir.cpp.types;

import se.lth.cs.tycho.ir.AbstractIRNode;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.type.Type;

public abstract class CppType extends AbstractIRNode {


    /**
     * Constructs a node with the same line and column numbers as {@code original}.
     *
     * @param original the original node
     */
    public CppType(IRNode original, boolean isPointer) {
        super(original);
        this.isPointer = isPointer;
    }
    private final boolean isPointer;

    public boolean isPointer() {
        return isPointer;
    }

    @Override
    public abstract String toString();
}
