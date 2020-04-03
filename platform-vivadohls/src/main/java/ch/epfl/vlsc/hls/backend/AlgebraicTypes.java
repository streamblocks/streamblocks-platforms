package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.DefaultValues;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.Pair;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.*;

import java.util.*;
import java.util.stream.Stream;

@Module
public interface AlgebraicTypes {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default DefaultValues defaultValues() {
        return backend().defaultValues();
    }

    default Emitter emitter() {
        return backend().emitter();
    }

    default void declareAlgebraicTypes() {
        types().forEachOrdered(this::declareType);
        emitter().emitNewLine();
    }

    default void declareType(AlgebraicType type) {
        emitter().emit("class %s {", type.getName());
        emitter().emit("public:");
        {
            emitter().increaseIndentation();

            if (type instanceof SumType) {
                SumType sum = (SumType) type;

                // -- Sum Tag
                emitter().emit("enum Tag {");
                {
                    emitter().increaseIndentation();

                    emitter().emit("%s___None = 0,", sum.getName());
                    Iterator<SumType.VariantType> iter = sum.getVariants().iterator();
                    while (iter.hasNext()) {
                        SumType.VariantType variant = iter.next();
                        if (iter.hasNext()) {
                            emitter().emit("%s___%s,", sum.getName(), variant.getName());
                        } else {
                            emitter().emit("%s___%s", sum.getName(), variant.getName());
                        }
                    }

                    emitter().decreaseIndentation();
                }
                emitter().emit("};");
                emitter().emitNewLine();

                emitter().emit("Tag tag;");
                emitter().emitNewLine();

                // -- Sum Variants
                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("struct %s{", variant.getName());
                    {
                        emitter().increaseIndentation();

                        variant.getFields().forEach(field -> emitter().emit("%s;", backend().declarations().declaration(field.getType(), field.getName())));

                        emitter().decreaseIndentation();
                    }
                    emitter().emit("};");
                }
                emitter().emitNewLine();

                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("struct %s %1$s;", variant.getName());
                }
                emitter().emitNewLine();
            } else {
                ProductType product = (ProductType) type;
                product.getFields().forEach(field -> {
                    emitter().emit("%s;", backend().declarations().declaration(field.getType(), field.getName()));
                });

                emitter().emitNewLine();
            }

            // -- Default Constructor
            emitter().emit("%s() {", type.getName());
            {
                emitter().increaseIndentation();

                if (type instanceof SumType) {
                    SumType sum = (SumType) type;

                    emitter().emit("tag = %s___None;", sum.getName());
                    emitter().emitNewLine();

                    for (SumType.VariantType variant : sum.getVariants()) {
                        for (FieldType field : variant.getFields()) {
                            if (field.getType() instanceof AlgebraicType) {
                                emitter().emit("%s.%s = %s();", variant.getName(), field.getName(), ((AlgebraicType) field.getType()).getName());
                            } else {
                                emitter().emit("%s.%s = %s;", variant.getName(), field.getName(), defaultValues().defaultValue(field.getType()));
                            }
                        }
                    }
                } else {
                    ProductType product = (ProductType) type;
                    for (FieldType field : product.getFields()) {
                        if (field.getType() instanceof AlgebraicType) {
                            emitter().emit("%s = %s();", field.getName(), ((AlgebraicType) field.getType()).getName());
                        } else {
                            emitter().emit("%s = %s;", field.getName(), defaultValues().defaultValue(field.getType()));
                        }
                    }
                }
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            // -- Copy Constructor
            emitter().emit("%s(const %1$s &t) {", type.getName());
            {
                emitter().increaseIndentation();

                if (type instanceof SumType) {
                    SumType sum = (SumType) type;

                    emitter().emit("tag = t.tag;");
                    emitter().emitNewLine();

                    for (SumType.VariantType variant : sum.getVariants()) {
                        for (FieldType field : variant.getFields()) {
                            emitter().emit("%s.%s = t.%1$s.%2$s;", variant.getName(), field.getName());
                        }
                    }
                } else {
                    ProductType product = (ProductType) type;
                    for (FieldType field : product.getFields()) {
                        emitter().emit("%s = t.%1$s;", field.getName());
                    }
                }

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            // -- Constructor
            if (type instanceof SumType) {
                SumType sum = (SumType) type;
                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("%s %1$s_%s(%s){", type.getName(), variant.getName(), String.join(", ", fieldTypessAsList(variant.getFields())));
                    {
                        emitter().increaseIndentation();

                        emitter().emit("%s t = %1$s();", sum.getName());
                        emitter().emit("t.tag = %s___%s;", type.getName(), variant.getName());
                        for (FieldType field : variant.getFields()) {
                            emitter().emit("t.%s.%s = %2$s;", variant.getName(), field.getName());
                        }
                        emitter().emitNewLine();

                        emitter().emit("return t;");
                        emitter().decreaseIndentation();
                    }
                    emitter().emit("}");
                    emitter().emitNewLine();
                }
            } else {
                ProductType product = (ProductType) type;
                emitter().emit("%s(%s) {", type.getName(), String.join(", ", fieldTypessAsList(product.getFields())));
                {
                    emitter().increaseIndentation();

                    product.getFields().forEach(f -> emitter().emit("this->%s = %1$s;", f.getName()));

                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            }
            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();
    }

    default String constructor(String constructor) {
        return types()
                .filter(type -> {
                    if (type instanceof ProductType) {
                        return Objects.equals(type.getName(), constructor);
                    } else {
                        return ((SumType) type).getVariants().stream().anyMatch(variant -> Objects.equals(variant.getName(), constructor));
                    }
                })
                .map(type -> {
                    if (type instanceof ProductType) {
                        return type.getName();
                    } else {
                        return ((SumType) type).getVariants().stream().filter(variant -> Objects.equals(variant.getName(), constructor)).map(variant -> String.format("%s().%1$s_%s", type.getName(), variant.getName())).findAny().get();
                    }
                })
                .findAny()
                .get();
    }

    default List<String> fieldTypessAsList(List<FieldType> list) {
        List<String> fields = new ArrayList<>();
        list.forEach(field -> fields.add(String.format("%s", backend().declarations().declaration(field.getType(), field.getName()))));
        return fields;
    }

    default Stream<AlgebraicType> types() {
        return backend().task()
                .getSourceUnits().stream()
                .flatMap(unit -> unit.getTree().getTypeDecls().stream())
                .map(decl -> (AlgebraicType) backend().types().declaredGlobalType(decl));
    }


    default List<Map.Entry<String, Type>> flattenFieldNames(AlgebraicType type, String extension) {
        List<Map.Entry<String, Type>> names = new ArrayList<>();

        if (type instanceof SumType) {
            SumType sum = (SumType) type;
            for (SumType.VariantType variant : sum.getVariants()) {
                for (FieldType field : variant.getFields()) {
                    if (field.getType() instanceof AlgebraicType) {
                        names.addAll(flattenFieldNames((AlgebraicType) field.getType(), field.getName() + "_"));
                    } else {
                        names.add(Pair.of(extension + field.getName(), field.getType()));
                    }
                }
            }
        } else {
            ProductType product = (ProductType) type;
            for (FieldType field : product.getFields()) {
                if (field.getType() instanceof AlgebraicType) {
                    names.addAll(flattenFieldNames((AlgebraicType) field.getType(), field.getName() + "_"));
                } else {
                    names.add(Pair.of(extension + field.getName(), field.getType()));
                }
            }
        }
        return names;
    }

}
