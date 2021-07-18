package ch.epfl.vlsc.wsim.phase;

import ch.epfl.vlsc.wsim.entity.ir.cpp.CppNominalTypeExpr;

import ch.epfl.vlsc.wsim.entity.ir.cpp.types.ArrayCppType;
import ch.epfl.vlsc.wsim.entity.ir.cpp.types.CppType;
import ch.epfl.vlsc.wsim.entity.ir.cpp.types.NativeTypeCpp;

import ch.epfl.vlsc.wsim.entity.ir.cpp.types.VectorCppType;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.IRNode;

import se.lth.cs.tycho.ir.type.NominalTypeExpr;

import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import se.lth.cs.tycho.type.*;

import org.multij.Module;



public class CppTypeConversionPhase implements Phase {


    @Override
    public String getDescription() {
        return "Lower actor nominal types to c++ nominal types";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        TypeConversion conversion = MultiJ.from(TypeConversion.class).instance();

        LowerToCppTypesTransformation transformation = MultiJ.from(LowerToCppTypesTransformation.class)
                .bind("tree").to(task.getModule(TreeShadow.key))
                .bind("highLevelTypesExpression").to(task.getModule(Types.key))
                .bind("conversion").to(conversion)
                .bind("context").to(context).instance();
        CompilationTask transformedTask = transformation.applyChecked(CompilationTask.class, task);
        return transformedTask;
    }

    @Module
    interface LowerToCppTypesTransformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        Types highLevelTypesExpression();

        @Binding(BindingKind.INJECTED)
        TypeConversion conversion();

        @Binding(BindingKind.INJECTED)
        TreeShadow tree();

        @Binding(BindingKind.INJECTED)
        Context context();

        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default CppNominalTypeExpr apply(NominalTypeExpr typeExpr) {
            Type highLevelType =  highLevelTypesExpression().type(typeExpr);
            CppNominalTypeExpr cppTypeExpr = null;
            try {
                cppTypeExpr = new CppNominalTypeExpr(conversion().convert(highLevelType));
            } catch (CompilationException e) {
               context().getReporter().report(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "Error converting CAL nominal type to C++ types : \n" +
                                        e.getDiagnostic().generateMessage(),
                                findSourceUnit(typeExpr), typeExpr)
                );
            }
            return cppTypeExpr;
        }

        default SourceUnit findSourceUnit(IRNode node) {
            return findSourceUnit(tree().parent(node));
        }
        default SourceUnit findSourceUnit(SourceUnit node) {
            return node;
        }


    }

    /**
     * A functional interface for converting high level CAL types to lower level
     * C++ types
     */

    @Module
    interface TypeConversion {


        default <T extends Type> CppType unimplConv(Class<T> type) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            "Unimplemented type conversion  ")
            );

        }

        CppType convert(Type t);

        default CppType convert(BottomType t) {
            return unimplConv(t.getClass());
        }

        default CppType convert(ListType t) {
            Type elementType = t.getElementType();
            if (t.getSize().isPresent()) {
                return new ArrayCppType(convert(elementType), t.getSize().getAsInt());
            } else {
                return new VectorCppType(convert(elementType));
            }
        }

        default CppType convert(SetType t) {
            return unimplConv(t.getClass());
        }

        default CppType convert(MapType t) {
            return unimplConv(t.getClass());
        }

        default CppType convert(QueueType t) {
            return unimplConv(t.getClass());
        }

        default CppType convert(IntType t) {
            final int size = t.getSize().orElse(32);
            final boolean signed = t.isSigned();
            if (size == 1) {
                return NativeTypeCpp.Bool();
            } else {
                if (size <= 8) {
                    return NativeTypeCpp.Int8(signed);
                } else if (size <= 16) {
                    return NativeTypeCpp.Int16(signed);
                } else if (size <= 32) {
                    return NativeTypeCpp.Int32(signed);
                } else if (size <= 64) {
                    return NativeTypeCpp.Int64(signed);
                } else {
                    throw new CompilationException(new Diagnostic(
                            Diagnostic.Kind.ERROR, "Can not convert " + t.toString() +
                            " to C++ native types."
                    ));
                }
            }
        }


        default CppType convert(BoolType t) {
            return NativeTypeCpp.Bool();
        }

        default CppType convert(CharType t) {
            return NativeTypeCpp.Char();
        }

        default CppType convert(UnitType t) {
            return NativeTypeCpp.Void();
        }

        default CppType convert(RealType t) {

            final int size = t.getSize();
            if (size <= 32) {
                return NativeTypeCpp.Float();
            } else if (size <= 64) {
                return NativeTypeCpp.Double();
            } else {
                throw new CompilationException(
                        new Diagnostic(
                                Diagnostic.Kind.ERROR,
                                "Can not convert " + t.toString() + " to C++ native types."
                        )
                );
            }
        }

        // remove aliasing
        default CppType convert(AliasType t) {
            return convert(t.getType());
        }

        default CppType convert(SumType t) {
            return unimplConv(t.getClass());
        }

        default CppType convert(ProductType t) {
            return unimplConv(t.getClass());
        }
    }


}
