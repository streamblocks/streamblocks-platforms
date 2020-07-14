package ch.epfl.vlsc.phases;

import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.settings.SizeValueParser;
import org.multij.BindingKind;
import org.multij.MultiJ;

import se.lth.cs.tycho.attribute.Types;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.SourceUnit;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;

import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.Expression;

import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;
import se.lth.cs.tycho.settings.Setting;

import se.lth.cs.tycho.type.Type;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.multij.Module;
import org.multij.Binding;

public class AnnotateExternalMemories implements Phase {



    @Override
    public String getDescription() {
        return "Annotates actor variables that should be store on an external memory";
    }

    private Long sizeThreshold;
    private CompilationTask task;
    private Context context;

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(PlatformSettings.maxBRAMSize);
    }

    /**
     *
     * @param task
     * @param context
     * @return
     * @throws CompilationException
     */
    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        this.task = task;
        this.context = context;
        if (context.getConfiguration().get(PlatformSettings.PartitionNetwork)) {
            this.sizeThreshold = new SizeValueParser()
                    .parse(context.getConfiguration().get(PlatformSettings.maxBRAMSize));
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Maximum bram size: " +
                            context.getConfiguration().get(PlatformSettings.maxBRAMSize)
                            + " (" + this.sizeThreshold + "B)"));

            Transformation transform = MultiJ.from(Transformation.class)
                    .bind("reporter").to(context.getReporter())
                    .bind("tree").to(task.getModule(TreeShadow.key))
                    .bind("types").to(task.getModule(Types.key))
                    .bind("memories").to(task.getModule(Memories.key))
                    .bind("maxInternalMemory").to(this.sizeThreshold)
                    .instance();
            CompilationTask transformedTask = task.transformChildren(transform);
            return transformedTask;
        } else {
            return task;
        }


    }


    @Module
    interface Transformation extends IRNode.Transformation {

        @Binding(BindingKind.INJECTED)
        Reporter reporter();

        @Binding(BindingKind.INJECTED)
        TreeShadow tree();

        @Binding(BindingKind.INJECTED)
        Types types();

        @Binding(BindingKind.INJECTED)
        Memories memories();

        @Binding(BindingKind.INJECTED)
        Long maxInternalMemory();


        @Override
        default IRNode apply(IRNode node) {
            return node.transformChildren(this);
        }
        default ParameterVarDecl apply(ParameterVarDecl varDecl) {

            if (shouldBeExternal(varDecl)) {
                warnIfNotAligned(varDecl);

                TypeExpr type = (TypeExpr) varDecl.getType().deepClone();

                List<Annotation> annotations =
                        varDecl.getAnnotations().stream().map(Annotation::deepClone).collect(Collectors.toList());
                annotations.add(getAnnotation());

                String name = varDecl.getName();
                Expression defaultValue = varDecl.getDefaultValue() == null ? null : varDecl.getDefaultValue().deepClone();

                return new ParameterVarDecl(annotations, type, name, defaultValue);
            } else {
                return (ParameterVarDecl) varDecl.deepClone();
            }

        }

        default LocalVarDecl apply(LocalVarDecl varDecl) {

            if (shouldBeExternal(varDecl)) {
                warnIfNotAligned(varDecl);

                TypeExpr type = (TypeExpr) varDecl.getType().deepClone();


                List<Annotation> annotations =
                        varDecl.getAnnotations().stream().map(Annotation::deepClone).collect(Collectors.toList());
                annotations.add(getAnnotation());

                boolean constant = varDecl.isConstant();
                boolean external = varDecl.isExternal(); // for callable var decls

                Expression expr = varDecl.getValue() == null ? null : varDecl.getValue().deepClone();

                String name = varDecl.getName();

                return new LocalVarDecl(annotations, type, name, expr, constant);
            } else {
                return (LocalVarDecl) varDecl.deepClone();
            }
        }

        default Boolean shouldBeExternal(VarDecl decl) {
            Type type = types().declaredType(decl);

            Optional<Long> bitsInType = memories().sizeInBytes(type);

            if (bitsInType.isPresent() && bitsInType.get() > maxInternalMemory()) {

                return true;
            }

            // Either we could not compute the memory size or the variable fits in the given limits
            return false;
        }

        default void warnIfNotAligned(VarDecl decl) {
            Type type = types().declaredType(decl);

            reporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO,
                            String.format("Variable %s (estimated byte-aligned size = %s B) is going to be mapped to external memories",
                                    decl.getOriginalName(), memories().sizeInBytes(type).get()), sourceUnit(decl), decl));


            if (!memories().isPowerOfTwo(type))
                reporter().report(new Diagnostic(Diagnostic.Kind.ERROR,
                        String.format("%s of type %s is not power-of-two, but is inferred as external memory.",
                                decl.getOriginalName(), type.toString()),
                        sourceUnit(decl), decl));
        }

        default Annotation getAnnotation() {
            AnnotationParameter param = new AnnotationParameter("external",
                    new ExprLiteral(ExprLiteral.Kind.True));
            Annotation annot = new Annotation("HLS", ImmutableList.of(param));
            return annot;
        }



        default SourceUnit sourceUnit(IRNode node) {
            return sourceUnit(tree().parent(node));
        }
        default SourceUnit sourceUnit(SourceUnit unit) {
            return unit;
        }


    }


}
