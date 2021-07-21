package ch.epfl.vlsc.wsim.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

import java.util.Objects;
import java.util.function.Consumer;

public class PairCppType extends  LibraryObjectCppType{


    private final CppType typeFirst;
    private final CppType typeSecond;

    public PairCppType(IRNode original, CppType typeFirst, CppType typeSecond) {
        super(original, false);
        this.typeFirst = typeFirst;
        this.typeSecond = typeSecond;
    }

    public PairCppType(CppType typeFirst, CppType typeSecond) {
        this(null, typeFirst, typeSecond);
    }



    @Override
    public String toString() {
        return "::std::pair<" + typeFirst.toString() + ", " + typeSecond.toString() + ">";
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(typeFirst);
        action.accept(typeSecond);
    }

    @Override
    public PairCppType transformChildren(Transformation transformation) {
        return copy(
            transformation.applyChecked(CppType.class, typeFirst),
                transformation.applyChecked(CppType.class, typeSecond)
        );
    }


    public PairCppType copy(CppType typeFirst, CppType typeSecond) {
        if (Objects.equals(typeFirst, this.typeFirst) && Objects.equals(typeSecond, this.typeSecond)) {
            return this;
        } else {
            return new PairCppType(typeFirst, typeSecond);
        }
    }

    public CppType getTypeFirst() {
        return typeFirst;
    }
    public CppType getTypeSecond() {
        return typeSecond;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof PairCppType)) {
            return false;
        } else {
            PairCppType castedType = (PairCppType) that;
            return Objects.equals(typeFirst, castedType.getTypeFirst()) &&
                    Objects.equals(typeSecond, castedType.getTypeSecond());
        }
    }
}
