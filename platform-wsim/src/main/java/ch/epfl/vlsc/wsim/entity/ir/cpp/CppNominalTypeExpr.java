package ch.epfl.vlsc.wsim.entity.ir.cpp;

import ch.epfl.vlsc.wsim.entity.ir.cpp.types.CppType;
import se.lth.cs.tycho.ir.AbstractIRNode;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;



import java.util.Objects;
import java.util.function.Consumer;


/**
 * IRNode representing a C++ nominal type
 */
public class CppNominalTypeExpr extends AbstractIRNode implements Cloneable, TypeExpr<CppNominalTypeExpr> {



    private final CppType type; // name of the type, e.g., std::shared_ptr


    public CppNominalTypeExpr(CppNominalTypeExpr original, CppType type) {
        super(original);
        this.type  = type;
    }
    public CppNominalTypeExpr(CppType type) {
        this(null, type);
    }

    public CppType getType() { return type;}


    private CppNominalTypeExpr copy(CppType type) {
        if (Objects.equals(this.type, type)) {
            return this;
        } else {
            return new CppNominalTypeExpr(type);
        }
    }


    @Override
    public void forEachChild(Consumer<? super IRNode> action) {

    }

    @Override
    public CppNominalTypeExpr transformChildren(Transformation transformation) {
        return this;
    }

    @Override
    public String toString() {
        return type.toString();
    }


}
