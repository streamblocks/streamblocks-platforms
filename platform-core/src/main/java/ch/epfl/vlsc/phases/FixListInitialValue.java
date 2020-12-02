package ch.epfl.vlsc.phases;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.GlobalVarDecl;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.expr.ExprList;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.type.*;

public class FixListInitialValue implements Phase {
    @Override
    public String getDescription() {
        return "null";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        Transformation transformation = MultiJ.from(Transformation.class)
                .bind("types").to(task.getModule(Types.key))
                .instance();

        return task.transformChildren(transformation);
    }

    @Module
    interface Transformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        Types types();


        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default IRNode apply(GlobalVarDecl global) {
            if (global.getValue() != null) {
                if (global.getValue() instanceof ExprList) {
                    ExprList list = (ExprList) global.getValue();

                    Type declType = types().declaredType(global);

                    Type exprType = types().type(list);

                    boolean sameSize = checkSize((ListType) declType, (ListType) exprType);

                    if (sameSize) {
                        return global;
                    }

                    ExprList copy = copy((ListType) declType, list);

                    return global.withValue(copy);
                }
            }

            return global;
        }

        default IRNode apply(LocalVarDecl local) {
            if (local.getValue() != null) {
                if (local.getValue() instanceof ExprList) {
                    ExprList list = (ExprList) local.getValue();

                    Type declType = types().declaredType(local);

                    Type exprType = types().type(list);

                    boolean sameSize = checkSize((ListType) declType, (ListType) exprType);

                    if (sameSize) {
                        return local;
                    }

                    ExprList copy = copy((ListType) declType, list);

                    return local.withValue(copy);
                }
            }

            return local;
        }


        default boolean checkSize(ListType from, ListType to) {
            boolean ret = true;

            if (to.getSize().isPresent() && to.getSize().equals(from.getSize())) {
                if (from.getElementType() instanceof ListType) {
                    if (to.getElementType() instanceof ListType) {
                        ret &= checkSize((ListType) from.getElementType(), (ListType) to.getElementType());
                    }
                }
            } else {
                return false;
            }

            return ret;
        }


        Expression defaultValue(Type type);

        default Expression defaultValue(BoolType type) {
            return new ExprLiteral(ExprLiteral.Kind.False);
        }

        default Expression defaultValue(CharType type) {
            return new ExprLiteral(ExprLiteral.Kind.Char, "");
        }

        default Expression defaultValue(StringType type) {
            return new ExprLiteral(ExprLiteral.Kind.String, "");
        }

        default Expression defaultValue(IntType type) {
            return new ExprLiteral(ExprLiteral.Kind.Integer, "0");
        }

        default Expression defaultValue(RealType type) {
            return new ExprLiteral(ExprLiteral.Kind.Real, "0.0");
        }

        default Expression defaultValue(ListType type) {
            ImmutableList.Builder<Expression> value = ImmutableList.builder();
            if (type.getSize().isPresent()) {
                for (int i = 0; i < type.getSize().getAsInt(); i++) {
                    value.add(defaultValue(type.getElementType()));
                }
            } else {
                throw new UnsupportedOperationException("FixListInitialValue : not supported");
            }

            return new ExprList(value.build());
        }

        default ExprList copy(ListType type, ExprList list) {
            ImmutableList.Builder<Expression> value = ImmutableList.builder();
            if (type.getSize().isPresent()) {
                int t = 0;

                for (int i = 0; i < list.getElements().size(); i++) {
                    if(list.getElements().get(i) instanceof ExprList){
                        value.add(copy((ListType)type.getElementType(), (ExprList) list.getElements().get(i)));
                    }else{
                        value.add(list.getElements().get(i).deepClone());
                    }
                    t++;
                }

                for (int i = t; i < type.getSize().getAsInt(); i++) {
                    value.add(defaultValue(type.getElementType()));
                }
            } else {
                throw new UnsupportedOperationException("FixListInitialValue : not supported");
            }

            return new ExprList(value.build());
        }

    }


}