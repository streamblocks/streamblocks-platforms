package ch.epfl.vlsc.wsim.entity.ir.cpp.types;


import se.lth.cs.tycho.ir.IRNode;

import java.util.Objects;
import java.util.function.Consumer;

public class ArrayCppType extends LibraryObjectCppType{

    private final CppType elementType;
    private final int arraySize;

    public ArrayCppType(CppType elementType, int arraySize, boolean isPointer) {
        this(null, elementType, arraySize, isPointer);
    }

    public ArrayCppType(CppType elementType, int arraySize) {
        this(null, elementType, arraySize, false);
    }
    public ArrayCppType(ArrayCppType original, CppType elementType, int arraySize, boolean isPointer) {
        super(original, isPointer);
        this.elementType = elementType;
        this.arraySize = arraySize;
    }
    @Override
    public String toString() { return "::std::array<" + elementType + ", " + arraySize + ">"; }

    public CppType getElementType() { return this.elementType; }
    public int getArraySize() { return this.arraySize; }

    public ArrayCppType withElementType(CppType elementType) {
        return copy(elementType, this.arraySize, isPointer());
    }

    public ArrayCppType withArraySize(int arraySize) {
        return copy(this.elementType, arraySize, isPointer());
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof  ArrayCppType)) {
            return false;
        } else {
            ArrayCppType castedType = (ArrayCppType) that;
            return Objects.equals(this.elementType, castedType.getElementType()) &&
                    this.arraySize == ((ArrayCppType) that).arraySize &&
                    this.isPointer() == castedType.isPointer();
        }
    }

    private ArrayCppType copy(CppType elementType, int arraySize, boolean isPointer) {
        if (Objects.equals(elementType, this.elementType) &&
                arraySize == this.arraySize &&
                isPointer == this.isPointer())
            return this;
        else
            return new ArrayCppType(elementType, arraySize, isPointer);
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(elementType);
    }

    @Override
    public IRNode transformChildren(Transformation transformation) {
        return copy(transformation.applyChecked(CppType.class, this.getElementType()),
                arraySize, isPointer());
    }
}
