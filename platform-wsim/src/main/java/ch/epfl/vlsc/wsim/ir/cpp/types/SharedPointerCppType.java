package ch.epfl.vlsc.wsim.ir.cpp.types;


import se.lth.cs.tycho.ir.IRNode;


import java.util.Objects;
import java.util.function.Consumer;

public class SharedPointerCppType extends LibraryObjectCppType {

    private final CppType elementType;

    public SharedPointerCppType(CppType elementType) {
        this(null, elementType);
    }
    public SharedPointerCppType(VectorCppType original, CppType elementType) {
        super(original, false);
        this.elementType = elementType;

    }

    public CppType getElementType() { return elementType; }

    @Override
    public String toString() {
        return "::std::shared_ptr<" + elementType.toString() + ">";
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof SharedPointerCppType)) {
            return false;
        } else {
            SharedPointerCppType thatCasted = (SharedPointerCppType) that;
            return Objects.equals(this.getElementType(), thatCasted.getElementType());
        }

    }

    private SharedPointerCppType copy(CppType elementType) {
        if (Objects.equals(this.getElementType(), elementType)) {
            return this;
        } else {
            return new SharedPointerCppType(elementType);
        }
    }
    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(elementType);
    }

    @Override
    public IRNode transformChildren(Transformation transformation) {
        return copy(
                transformation.applyChecked(CppType.class, this.getElementType()));
    }
}
