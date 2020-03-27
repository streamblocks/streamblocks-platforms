package ch.epfl.vlsc.sw.backend;

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
import java.util.Objects;
import java.util.stream.Stream;

@Module
public interface AlgebraicTypes {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void declareAlgebraicTypes() {
        types().forEachOrdered(this::declareTypedef);
        emitter().emitNewLine();

        types().forEachOrdered(this::declareType);
        emitter().emitNewLine();

        types().forEachOrdered(this::declareHelperTypePrototypes);
    }

    default void defineAlgebraicTypeHelperFunctions() {
        types().forEachOrdered(this::defineHelperTypeFunctions);
    }

    default void declareTypedef(AlgebraicType type) {
        emitter().emit("typedef struct %s_s %1$s_t;", type.getName());
    }

    void declareType(AlgebraicType type);

    default void declareType(ProductType product) {
        emitter().emit("enum %s_tags {", product.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("%s___none = 0", product.getName());

            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();

        emitter().emit("struct %s_s {", product.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("uint32_t flags;");
            emitter().emit("enum %s_tags tag;", product.getName());
            emitter().emit("union {");
            {
                emitter().increaseIndentation();

                emitter().emit("struct {");
                {
                    emitter().increaseIndentation();

                    product.getFields().forEach(field -> {
                        emitter().emit("%s;", backend().declarations().declaration(field.getType(), field.getName()));
                    });

                    emitter().decreaseIndentation();
                }

                emitter().emit("};");

                emitter().decreaseIndentation();
            }
            emitter().emit("} members;");

            emitter().decreaseIndentation();
        }

        emitter().emit("};");
        emitter().emitNewLine();
    }

    default void declareType(SumType sum) {
        emitter().emit("enum %s_tags {", sum.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("%s___none = 0,", sum.getName());
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

        emitter().emit("struct %s_s {", sum.getName());
        {
            emitter().increaseIndentation();

            emitter().emit("uint32_t flags;");
            emitter().emit("enum %s_tags tag;", sum.getName());
            emitter().emit("union {");
            {
                emitter().increaseIndentation();

                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("struct {");
                    {
                        emitter().increaseIndentation();

                        variant.getFields().forEach(field -> {
                            emitter().emit("%s;", backend().declarations().declaration(field.getType(), field.getName()));
                        });

                        emitter().decreaseIndentation();
                    }
                    emitter().emit("} %s;", variant.getName());
                }

                emitter().decreaseIndentation();
            }
            emitter().emit("} members;");

            emitter().decreaseIndentation();
        }

        emitter().emit("};");
        emitter().emitNewLine();

    }

    void declareHelperTypePrototypes(AlgebraicType type);

    default void declareHelperTypePrototypes(ProductType product) {
        emitter().emit("%s;", freeStructPrototype(product));
        emitter().emit("%s;", copyStructPrototype(product));
        emitter().emit("%s;", constructStructPrototype(product));
        emitter().emit("%s;", serializeStructPrototype(product));
        emitter().emit("%s;", deserializeStructPrototype(product));
        emitter().emit("%s;", sizeStructPrototype(product));

        emitter().emitNewLine();
    }

    default void declareHelperTypePrototypes(SumType sum) {
        emitter().emit("%s;", freeStructPrototype(sum));
        emitter().emit("%s;", copyStructPrototype(sum));

        sum.getVariants().forEach(variant -> {
            emitter().emit("%s;", constructStructVariantPrototype(sum, variant));
        });

        emitter().emit("%s;", serializeStructPrototype(sum));
        emitter().emit("%s;", deserializeStructPrototype(sum));
        emitter().emit("%s;", sizeStructPrototype(sum));
        emitter().emitNewLine();
    }

    default void defineHelperTypeFunctions(AlgebraicType type) {
        freeStructDefinition(type);
        copyStructDefinition(type);
        constructStructDefinition(type);
        serializeStructDefinition(type);
        deserializeStructDefinition(type);
        sizeStructDefinition(type);
    }


    default List<String> fieldTypessAsList(List<FieldType> list) {
        List<String> fields = new ArrayList<>();
        list.forEach(field -> {
            fields.add(String.format("%s", backend().declarations().declaration(field.getType(), field.getName())));
        });
        return fields;
    }

    default Stream<AlgebraicType> types() {
        return backend().task()
                .getSourceUnits().stream()
                .flatMap(unit -> unit.getTree().getTypeDecls().stream())
                .map(decl -> (AlgebraicType) backend().types().declaredGlobalType(decl));
    }

    default String freeStructPrototype(AlgebraicType type) {
        return String.format("int freeStruct%s_t(%1$s_t *src, int top)", type.getName());
    }

    default String copyStructPrototype(AlgebraicType type) {
        return String.format("int copyStruct%s_t(%1$s_t **dst, %1$s_t * src)", type.getName());
    }

    default String constructStructPrototype(ProductType product) {
        return String.format("int construct%s_t(%1$s_t **dst, %s)", product.getName(), String.join(", ", fieldTypessAsList(product.getFields())));
    }

    default String constructStructVariantPrototype(SumType sum, SumType.VariantType variant) {
        if (variant.getFields().isEmpty()) {
            return String.format("int construct%s___%s(%1$s_t **dst)", sum.getName(), variant.getName());
        } else {
            return String.format("int construct%s___%s(%1$s_t **dst, %s)", sum.getName(), variant.getName(), String.join(", ", fieldTypessAsList(variant.getFields())));
        }
    }

    default String serializeStructPrototype(AlgebraicType type) {
        return String.format("char *serializeStruct%s_t(%1$s_t *src, char *dstbuf)", type.getName());
    }

    default String deserializeStructPrototype(AlgebraicType type) {
        return String.format("char *deserializeStruct%s_t(%1$s_t **dst, char *srcbuf)", type.getName());
    }

    default String sizeStructPrototype(AlgebraicType type) {
        return String.format("long sizeStruct%s_t(%1$s_t *src)", type.getName());
    }

    default void freeStructDefinition(AlgebraicType type) {
        emitter().emit("%s {", freeStructPrototype(type));
        {
            emitter().increaseIndentation();

            emitter().emit("if (src == NULL) return 0;");
            emitter().emitNewLine();

            if (type instanceof ProductType) {
                ProductType product = (ProductType) type;
                for (FieldType field : product.getFields()) {
                    if (field.getType() instanceof AlgebraicType) {
                        emitter().emit("freeStruct%s_t(src->members.%s, 1);", ((AlgebraicType) field.getType()).getName(), field.getName());
                    }
                }
            } else if (type instanceof SumType) {
                SumType sum = (SumType) type;

                emitter().emit("switch(src->tag){");

                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("case %s___%s:", sum.getName(), variant.getName());
                    emitter().increaseIndentation();

                    for (FieldType field : variant.getFields()) {
                        if (field.getType() instanceof AlgebraicType) {
                            emitter().emit("freeStruct%s_t(src->members.%s.%s, 1);", ((AlgebraicType) field.getType()).getName(), variant.getName(), field.getName());
                        }
                    }

                    emitter().emit("break;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("default:");
                emitter().emit("\tbreak;");

                emitter().emit("}");
            }

            emitter().emit("if(top && (src->flags & 0x1) == 0x1) {");
            emitter().emit("\tfree(src);");
            emitter().emit("}");
            emitter().emitNewLine();

            emitter().emit("return 1;");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void copyStructDefinition(AlgebraicType type) {
        emitter().emit("%s {", copyStructPrototype(type));
        {
            emitter().increaseIndentation();
            // -- If temp and on heap just steal the src
            emitter().emit("if((src->flags & 0x3) == 0x3) {");
            {
                emitter().increaseIndentation();
                // -- Make sure that we don't leak memory if dst already pointing to object
                emitter().emit("if(*dst != NULL) {");
                emitter().emit("\tfreeStruct%s_t(*dst, 1);", type.getName());
                emitter().emit("}");

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            // -- We need to copy - make sure dst is allocated
            emitter().emit("int flags;");
            emitter().emit("if(*dst == NULL){");
            emitter().emit("\t*dst = calloc(sizeof(**dst), 1);");
            emitter().emit("\tflags = 0x1;");
            emitter().emit("} else {");
            emitter().emit("\tflags = (*dst)->flags;");
            emitter().emit("}");


            emitter().emitNewLine();

            emitter().emit("memcpy(*dst, src, sizeof(**dst));");
            emitter().emit("(*dst)->flags = flags;");
            emitter().emitNewLine();

            if (type instanceof SumType) {
                SumType sum = (SumType) type;
                emitter().emit("switch(src->tag){");

                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("case %s___%s:", sum.getName(), variant.getName());
                    emitter().increaseIndentation();

                    for (FieldType field : variant.getFields()) {
                        if (field.getType() instanceof AlgebraicType) {
                            emitter().emit("(*dst)->members.%s.%s = NULL;", variant.getName(), field.getName());
                            emitter().emit("copyStruct%s_t(&(*dst)->members.%s.%s, src->members.%2$s.%3$s);", ((AlgebraicType) field.getType()).getName(), variant.getName(), field.getName());
                        }
                    }

                    emitter().emit("break;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("default:");
                emitter().emit("\tbreak;");

                emitter().emit("}");
            } else {
                ProductType product = (ProductType) type;
                for (FieldType field : product.getFields()) {
                    if (field.getType() instanceof AlgebraicType) {
                        emitter().emit("(*dst)->members.%s = NULL;", field.getName());
                        emitter().emit("copyStruct%s_t(&(*dst)->members.%s, src->members.%2$s);", ((AlgebraicType) field.getType()).getName(), field.getName());
                    }
                }
            }
            emitter().emit("return 1;");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    void constructStructDefinition(AlgebraicType type);

    default void constructStructDefinition(ProductType product) {
        emitter().emit("%s {", constructStructPrototype(product));
        {
            emitter().increaseIndentation();

            emitter().emit("if (*dst == NULL){");
            emitter().emit("\t*dst = calloc(sizeof(**dst), 1);");
            emitter().emit("}");
            emitter().emitNewLine();

            emitter().emit("(*dst)->flags = 0x1;");
            emitter().emit("(*dst)->tag = 0;");
            for (FieldType field : product.getFields()) {
                if (field.getType() instanceof AlgebraicType) {
                    emitter().emit("copyStruct%s_t(&(*dst)->members.%s, %2$s);", ((AlgebraicType) field.getType()).getName(), field.getName());
                } else {
                    emitter().emit("(*dst)->members.%s =  %1$s;", field.getName());
                }
            }

            emitter().emitNewLine();
            emitter().emit("return 1;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void constructStructDefinition(SumType sum) {
        for (SumType.VariantType variant : sum.getVariants()) {
            emitter().emit("%s {", constructStructVariantPrototype(sum, variant));
            {
                emitter().increaseIndentation();

                emitter().emit("if (*dst == NULL){");
                emitter().emit("\t*dst = calloc(sizeof(**dst), 1);");
                emitter().emit("}");
                emitter().emitNewLine();

                emitter().emit("(*dst)->flags = 0x1;");
                emitter().emit("(*dst)->tag = 0;");
                for (FieldType field : variant.getFields()) {
                    if (field.getType() instanceof AlgebraicType) {
                        emitter().emit("copyStruct%s_t(&(*dst)->members.%s.%s, %3$s);", ((AlgebraicType) field.getType()).getName(), variant.getName(), field.getName());
                    } else {
                        emitter().emit("(*dst)->members.%s.%s = %2$s;", variant.getName(), field.getName());
                    }
                }

                emitter().emitNewLine();
                emitter().emit("return 1;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();
        }
    }

    default void serializeStructDefinition(AlgebraicType type) {
        emitter().emit("%s {", serializeStructPrototype(type));
        {
            emitter().increaseIndentation();

            emitter().emit("char *p = dstbuf;");
            if (type instanceof SumType) {
                SumType sum = (SumType) type;
                emitter().emit("*(enum %s_tags *) p = src->tag;", type.getName());
                emitter().emit("p = (char *) ((enum %s_tags *) p + 1);", type.getName());

                emitter().emit("switch(src->tag){");

                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("case %s___%s:", sum.getName(), variant.getName());
                    emitter().increaseIndentation();

                    for (FieldType field : variant.getFields()) {
                        if (field.getType() instanceof AlgebraicType) {
                            emitter().emit("p = serializeStruct%s_t(src->members.%s.%s, p);", ((AlgebraicType) field.getType()).getName(), variant.getName(), field.getName());
                        } else {
                            String castStr = backend().typeseval().type(field.getType());
                            emitter().emit("*(%s *) p = src->members.%s.%s;", castStr, variant.getName(), field.getName());
                            emitter().emit("p = (char *) ((%s *) p + 1);", castStr);
                        }
                    }

                    emitter().emit("break;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("default:");
                emitter().emit("\tbreak;");

                emitter().emit("}");
            } else {
                ProductType product = (ProductType) type;
                for (FieldType field : product.getFields()) {
                    if (field.getType() instanceof AlgebraicType) {
                        emitter().emit("p = serializeStruct%s_t(src->members.%s, p);", ((AlgebraicType) field.getType()).getName(), field.getName());
                    } else {
                        String castStr = backend().typeseval().type(field.getType());
                        emitter().emit("*(%s *) p = src->members.%s;", castStr, field.getName());
                        emitter().emit("p = (char *) ((%s *) p + 1);", castStr);
                    }
                }
            }

            emitter().emitNewLine();
            emitter().emit("return p;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void deserializeStructDefinition(AlgebraicType type) {
        emitter().emit("%s {", deserializeStructPrototype(type));
        {
            emitter().increaseIndentation();

            emitter().emit("char *p = srcbuf;");
            emitter().emit("*dst = calloc(sizeof(**dst), 1);");
            emitter().emit("(*dst)->flags = 0x1;");

            if (type instanceof SumType) {
                SumType sum = (SumType) type;

                emitter().emit("(*dst)->tag = *(enum %s_tags *) p;", type.getName());
                emitter().emit("p = (char *) ((enum %s_tags *) p + 1);", type.getName());

                emitter().emit("switch((*dst)->tag) {");

                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("case %s___%s:", sum.getName(), variant.getName());
                    emitter().increaseIndentation();

                    for (FieldType field : variant.getFields()) {
                        if (field.getType() instanceof AlgebraicType) {
                            emitter().emit("p = deserializeStruct%s_t(&(*dst)->members.%s.%s, p);", ((AlgebraicType) field.getType()).getName(), variant.getName(), field.getName());
                        } else {
                            String castStr = backend().typeseval().type(field.getType());
                            emitter().emit("(*dst)->members.%s.%s = *(%s *) p;", variant.getName(), field.getName(), castStr);
                            emitter().emit("p = (char *) ((%s *) p + 1);", castStr);
                        }
                    }

                    emitter().emit("break;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("default:");
                emitter().emit("\tbreak;");

                emitter().emit("}");
            } else {
                ProductType product = (ProductType) type;
                for (FieldType field : product.getFields()) {
                    if (field.getType() instanceof AlgebraicType) {
                        emitter().emit("p = deserializeStruct%s_t(&(*dst)->members.%s, p);", ((AlgebraicType) field.getType()).getName(), field.getName());
                    } else {
                        String castStr = backend().typeseval().type(field.getType());
                        emitter().emit("(*dst)->members.%s = *(%s *) p;", field.getName(), castStr);
                        emitter().emit("p = (char *) ((%s *) p + 1);", castStr);
                    }
                }
            }

            emitter().emitNewLine();
            emitter().emit("return p;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void sizeStructDefinition(AlgebraicType type) {
        emitter().emit("%s {", sizeStructPrototype(type));
        {
            emitter().increaseIndentation();

            emitter().emit("long ret = 0;");

            if (type instanceof SumType) {
                SumType sum = (SumType) type;

                emitter().emit("ret += sizeof(enum %s_tags);", type.getName());
                emitter().emit("switch(src->tag){");

                for (SumType.VariantType variant : sum.getVariants()) {
                    emitter().emit("case %s___%s:", sum.getName(), variant.getName());
                    emitter().increaseIndentation();

                    for (FieldType field : variant.getFields()) {
                        if (field.getType() instanceof AlgebraicType) {
                            emitter().emit("ret += sizeStruct%s_t(src->members.%s.%s);", ((AlgebraicType) field.getType()).getName(), variant.getName(), field.getName());
                        } else {
                            emitter().emit("ret += sizeof(src->members.%s.%s);", variant.getName(), field.getName());
                        }
                    }

                    emitter().emit("break;");
                    emitter().decreaseIndentation();
                }

                emitter().emit("default:");
                emitter().emit("\tbreak;");

                emitter().emit("}");
            } else {
                ProductType product = (ProductType) type;
                for (FieldType field : product.getFields()) {
                    if (field.getType() instanceof AlgebraicType) {
                        emitter().emit("ret += sizeStruct%s_t(src->members.%s);", ((AlgebraicType) field.getType()).getName(), field.getName());
                    } else {
                        emitter().emit("ret += sizeof(src->members.%s);", field.getName());
                    }
                }
            }

            emitter().emitNewLine();
            emitter().emit("return ret;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default String type(AlgebraicType type) {
        return type.getName() + "_t";
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
                        return "construct" + type(type);
                    } else {
                        return ((SumType) type).getVariants().stream().filter(variant -> Objects.equals(variant.getName(), constructor)).map(variant -> "construct" + type.getName() + "___" + variant.getName()).findAny().get();
                    }
                })
                .findAny()
                .get();
    }

    default String destructor(AlgebraicType type) {
        return String.format("freeStruct%s_t", type.getName());
    }
}
