package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.DefaultValues;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.FieldType;
import se.lth.cs.tycho.type.ProductType;
import se.lth.cs.tycho.type.SumType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Module
public interface AlgebraicTypes {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    @Binding(BindingKind.INJECTED)
    DefaultValues defaultValues();

    default Emitter emitter() {
        return backend().emitter();
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

                    emitter().emit("%s_None = 0,", sum.getName());
                    Iterator<SumType.VariantType> iter = sum.getVariants().iterator();
                    while (iter.hasNext()) {
                        SumType.VariantType variant = iter.next();
                        if (iter.hasNext()) {
                            emitter().emit("%s_%s,", sum.getName(), variant.getName());
                        } else {
                            emitter().emit("%s_%s", sum.getName(), variant.getName());
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

                        variant.getFields().forEach(field -> {
                            emitter().emit("%s;", backend().declarations().declaration(field.getType(), field.getName()));
                        });

                        emitter().decreaseIndentation();
                    }
                    emitter().emit("};");
                }
                emitter().emitNewLine();

                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("struct %s %1$s;", variant.getName());
                }
                emitter().emitNewLine();
            }

            // -- Default Constructor
            emitter().emit("%s(){");
            {
                emitter().increaseIndentation();

                if (type instanceof SumType) {
                    SumType sum = (SumType) type;

                    emitter().emit("tag = %s_None;", sum.getName());
                    emitter().emitNewLine();

                    for (SumType.VariantType variant : sum.getVariants()) {
                        for (FieldType field : variant.getFields()) {
                            emitter().emit("%s.%s = %s;", variant.getName(), field.getName(), defaultValues().defaultValue(field.getType()));
                        }
                    }
                } else {
                    ProductType product = (ProductType) type;
                    for (FieldType field : product.getFields()) {
                        emitter().emit("%s = %s;", product.getName(), defaultValues().defaultValue(field.getType()));
                    }
                }
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            // -- Copy Constructor
            emitter().emit("%s(const %1$s &t){", type.getName());
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
                        emitter().emit("%s = t.%1$s;", product.getName());
                    }
                }

                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            // -- Constructor
            if (type instanceof SumType) {
                SumType sum = (SumType) type;

            } else {
                ProductType product = (ProductType) type;


            }


            emitter().decreaseIndentation();
        }
        emitter().emit("};");

    }

    default List<String> fieldTypessAsList(List<FieldType> list) {
        List<String> fields = new ArrayList<>();
        list.forEach(field -> {
            fields.add(String.format("%s", backend().declarations().declaration(field.getType(), field.getName())));
        });
        return fields;
    }

}
