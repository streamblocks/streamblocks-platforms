package ch.epfl.vlsc.wsim.phase;

import ch.epfl.vlsc.wsim.ir.cpp.CppNominalTypeExpr;

import ch.epfl.vlsc.wsim.phase.attributes.CppTypeConversion;
import ch.epfl.vlsc.wsim.phase.attributes.SourceUnitFinder;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.Generator;
import se.lth.cs.tycho.ir.IRNode;

import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.type.NominalTypeExpr;

import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import se.lth.cs.tycho.type.*;

import org.multij.Module;

import java.util.List;
import java.util.function.Predicate;


public class CppTypeConversionPhase implements Phase {


    @Override
    public String getDescription() {
        return "Lower actor nominal types to c++ nominal types";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        CppTypeConversion conversion = MultiJ.from(CppTypeConversion.class).instance();

        // set the generator types
        SetGeneratorVariableTypes generatorTypes = MultiJ.from(SetGeneratorVariableTypes.class)
                .bind("context").to(context)
                .bind("sources").to(task.getModule(SourceUnitFinder.key))
                .instance();
        CompilationTask withTypedGenerators = generatorTypes.applyChecked(CompilationTask.class, task);
        // lower to C++ types
        LowerToCppTypesTransformation transformation = MultiJ.from(LowerToCppTypesTransformation.class)
                .bind("tree").to(task.getModule(TreeShadow.key))
                .bind("highLevelTypesExpression").to(task.getModule(Types.key))
                .bind("conversion").to(conversion)
                .bind("context").to(context)
                .bind("sources").to(task.getModule(SourceUnitFinder.key))
                .instance();
        CompilationTask transformedTask = transformation.applyChecked(CompilationTask.class, withTypedGenerators);
        return transformedTask;
    }


    /**
     * Transformation to set the types for generator varDecls
     */
    @Module
    interface SetGeneratorVariableTypes extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        Context context();

        @Binding(BindingKind.INJECTED)
        SourceUnitFinder sources();

        default IRNode apply(IRNode node) { return node.transformChildren(this); }


        default IRNode apply(Generator gen) {

            ImmutableList<GeneratorVarDecl> decls = gen.getVarDecls();
            TypeExpr generatorType = gen.getType();
            Predicate<GeneratorVarDecl> invalidVarDecl = v ->
                    !(v.getType() == null || v.getType() == generatorType);
            decls.stream().filter(invalidVarDecl)
                    .forEach(v ->
                context().getReporter().report(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "generator variable declaration does have the same type as the generator.",
                                sources().find(v), v)
                )
            );
            if (!(generatorType instanceof NominalTypeExpr)) {
                context().getReporter().report(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "Only nominal generator types are supported for C++",
                                sources().find(gen), gen)
                );
            }

            Generator transformed = gen.withVarDecls(
                    decls.map(
                            v -> v.withType(
                                    (TypeExpr) generatorType.deepClone()
                            )));
            return transformed;
        }
    }

    /**
     * Lower CAL type expression to nominal C++ type expressions
     */
    @Module
    interface LowerToCppTypesTransformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        Types highLevelTypesExpression();

        @Binding(BindingKind.INJECTED)
        CppTypeConversion conversion();

        @Binding(BindingKind.INJECTED)
        TreeShadow tree();

        @Binding(BindingKind.INJECTED)
        Context context();

        @Binding(BindingKind.INJECTED)
        SourceUnitFinder sources();

        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }

        default SourceUnit apply(SourceUnit src) {
            return src.transformChildren(this);
        }

        // check for untyped variable declaration and fix them.
        default VarDecl apply(VarDecl varDecl) {
            VarDecl transformedDecl = varDecl.transformChildren(this);
            if (varDecl.getType() == null) { // if a declaration has no type try to infer it
                if (varDecl.getValue() != null) {
                    Type highLevel = highLevelTypesExpression().type(varDecl.getValue());
                    return transformedDecl
                            .withType(new CppNominalTypeExpr(
                                    conversion().convert(highLevel)));
                } else {
                    context().getReporter().report(
                            new Diagnostic(Diagnostic.Kind.ERROR,
                                    "Error handling untyped variable declaration.",
                                            findSourceUnit(varDecl), varDecl));
                    return transformedDecl;
                }
            } else {
                return transformedDecl;
            }
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

        default CppNominalTypeExpr apply(CppNominalTypeExpr typeExpr) {
            return typeExpr;
        }

        default SourceUnit findSourceUnit(IRNode node) {
            return sources().find(node);
        }

    }




}
