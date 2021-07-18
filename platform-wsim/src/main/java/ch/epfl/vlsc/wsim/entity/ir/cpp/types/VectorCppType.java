package ch.epfl.vlsc.wsim.entity.ir.cpp.types;


import ch.epfl.vlsc.wsim.entity.ir.cpp.CppNominalTypeExpr;
import ch.epfl.vlsc.wsim.entity.ir.cpp.expression.ExprCppMethod;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.Type;

import java.beans.Expression;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Vector;
import java.util.function.Consumer;

public final class VectorCppType extends LibraryObjectCppType {

    private final CppType elementType;
    private final ExprCppMethod sizeMethod;

    public VectorCppType(CppType elementType, boolean isPointer) {
        this(null, elementType, isPointer);
    }
    public VectorCppType(CppType elementType) {
        this(null, elementType, false);
    }
    public VectorCppType(VectorCppType original, CppType elementType, boolean isPointer) {
        super(original, isPointer);
        this.elementType = elementType;
        this.sizeMethod = new ExprCppMethod(ImmutableList.empty(),
                ImmutableList.empty(),
                new CppNominalTypeExpr(
                        new NativeTypeCpp(NativeTypeCpp.Kind.SIZE_T))
        );
    }

    public CppType getElementType() { return elementType; }

    @Override
    public String toString() {
        return "::std::vector<" + elementType.toString() + ">";
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
            VectorCppType thatCasted = (VectorCppType) that;
            return Objects.equals(this.getElementType(), thatCasted.getElementType()) &&
                    this.isPointer() == thatCasted.isPointer();
        }

    }

    private VectorCppType copy(CppType elementType, boolean isPointer) {
        if (Objects.equals(this.getElementType(), elementType) && isPointer == this.isPointer()) {
            return this;
        } else {
            return new VectorCppType(elementType);
        }
    }
    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(elementType);
    }

    @Override
    public IRNode transformChildren(Transformation transformation) {
        return copy(
                transformation.applyChecked(CppType.class, this.getElementType()),
                isPointer());
    }

}
