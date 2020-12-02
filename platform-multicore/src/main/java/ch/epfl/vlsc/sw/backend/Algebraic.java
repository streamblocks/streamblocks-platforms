package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.ir.decl.AlgebraicTypeDecl;
import se.lth.cs.tycho.type.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.multij.BindingKind.LAZY;

@Module
public interface Algebraic {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    @Binding(LAZY)
    default Forward forward() {
        return MultiJ.from(Forward.class)
                .bind("backend").to(backend())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Prototypes prototypes() {
        return MultiJ.from(Prototypes.class)
                .bind("typedef").to(typedef())
                .bind("init").to(init())
                .bind("free").to(free())
                .bind("write").to(write())
                .bind("read").to(read())
                .bind("size").to(size())
                .bind("copy").to(copy())
                .bind("compare").to(compare())
                .instance();
    }

    @Binding(LAZY)
    default Definitions definitions() {
        return MultiJ.from(Definitions.class)
                .bind("init").to(init())
                .bind("free").to(free())
                .bind("write").to(write())
                .bind("read").to(read())
                .bind("size").to(size())
                .bind("copy").to(copy())
                .bind("compare").to(compare())
                .instance();
    }

    @Binding(LAZY)
    default TypeDef typedef() {
        return MultiJ.from(TypeDef.class)
                .bind("backend").to(backend())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Init init() {
        return MultiJ.from(Init.class)
                .bind("backend").to(backend())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Free free() {
        return MultiJ.from(Free.class)
                .bind("backend").to(backend())

                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Write write() {
        return MultiJ.from(Write.class)
                .bind("backend").to(backend())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .bind("serialization").to(backend().serialization())
                .instance();
    }

    @Binding(LAZY)
    default Read read() {
        return MultiJ.from(Read.class)
                .bind("backend").to(backend())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .bind("serialization").to(backend().serialization())
                .instance();
    }

    @Binding(LAZY)
    default Size size() {
        return MultiJ.from(Size.class)
                .bind("backend").to(backend())

                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .bind("sizeof").to(backend().sizeof())
                .instance();
    }

    @Binding(LAZY)
    default Copy copy() {
        return MultiJ.from(Copy.class)
                .bind("backend").to(backend())

                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Compare compare() {
        return MultiJ.from(Compare.class)
                .bind("backend").to(backend())

                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Utils utils() {
        return MultiJ.from(Utils.class)
                .bind("backend").to(backend())
                .instance();
    }

    default void forwardAlgebraic() {
        backend().emitter().emit("// FORWARD TYPE DECLARATIONS");
        utils().types().forEach(type -> {
            forward().apply(type);
        });
    }

    default void declareAlgebraic() {
        backend().emitter().emit("// TYPE DECLARATIONS");
        utils().types().forEach(type -> {
            prototypes().apply(type);
        });
    }

    default void defineAlgebraic() {
        backend().emitter().emit("// TYPE DEFINITIONS");
        utils().types().forEach(type -> {
            definitions().apply(type);
        });
    }

    @Module
    interface Forward {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Emitter emitter();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        void apply(AlgebraicType type);

        default void apply(ProductType product) {
            emitter().emit("typedef %s* %s;", utils().internalName(product), utils().name(product));
            emitter().emit("");
        }

        default void apply(SumType sum) {
            emitter().emit("typedef %s* %s;", utils().internalName(sum), utils().name(sum));
            emitter().emit("");
        }
    }

    @Module
    interface Prototypes {

        @Binding(BindingKind.INJECTED)
        TypeDef typedef();

        @Binding(BindingKind.INJECTED)
        Init init();

        @Binding(BindingKind.INJECTED)
        Free free();

        @Binding(BindingKind.INJECTED)
        Write write();

        @Binding(BindingKind.INJECTED)
        Read read();

        @Binding(BindingKind.INJECTED)
        Size size();

        @Binding(BindingKind.INJECTED)
        Copy copy();

        @Binding(BindingKind.INJECTED)
        Compare compare();

        default void apply(AlgebraicType type) {
            typedef().apply(type);
            init().prototype(type);
            free().prototype(type);
            write().prototype(type);
            read().prototype(type);
            size().prototype(type);
            copy().prototype(type);
            compare().prototype(type);
        }
    }

    @Module
    interface Definitions {

        @Binding(BindingKind.INJECTED)
        Init init();

        @Binding(BindingKind.INJECTED)
        Free free();

        @Binding(BindingKind.INJECTED)
        Write write();

        @Binding(BindingKind.INJECTED)
        Read read();

        @Binding(BindingKind.INJECTED)
        Size size();

        @Binding(BindingKind.INJECTED)
        Copy copy();

        @Binding(BindingKind.INJECTED)
        Compare compare();

        default void apply(AlgebraicType type) {
            init().definition(type);
            free().definition(type);
            write().definition(type);
            read().definition(type);
            size().definition(type);
            copy().definition(type);
            compare().definition(type);
        }
    }

    @Module
    interface TypeDef {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Emitter emitter();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        void apply(AlgebraicType type);

        default void apply(ProductType product) {
            emitter().emit("%s {", utils().internalName(product));
            emitter().increaseIndentation();
            product.getFields().forEach(field -> {
                emitter().emit("%s;", backend().declarations().declaration(field.getType(), field.getName()));
            });
            emitter().decreaseIndentation();
            emitter().emit("};");
            emitter().emit("");
        }

        default void apply(SumType sum) {
            emitter().emit("enum %s_tag_t {", utils().mangle(sum.getName()));
            emitter().increaseIndentation();
            sum.getVariants().forEach(variant -> {
                emitter().emit("tag_%s_%s%s", utils().mangle(sum.getName()), utils().mangle(variant.getName()), sum.getVariants().indexOf(variant) == sum.getVariants().size() - 1 ? "" : ",");
            });
            emitter().decreaseIndentation();
            emitter().emit("};");
            emitter().emit("");
            emitter().emit("%s {", utils().internalName(sum));
            emitter().increaseIndentation();
            emitter().emit("enum %s_tag_t tag;", utils().mangle(sum.getName()));
            emitter().emit("union {");
            emitter().increaseIndentation();
            sum.getVariants().forEach(variant -> {
                emitter().emit("struct {");
                emitter().increaseIndentation();
                variant.getFields().forEach(field -> {
                    emitter().emit("%s;", backend().declarations().declaration(field.getType(), field.getName()));
                });
                emitter().decreaseIndentation();
                emitter().emit("} %s;", utils().mangle(variant.getName()));
            });
            emitter().decreaseIndentation();
            emitter().emit("} data;");
            emitter().decreaseIndentation();
            emitter().emit("};");
            emitter().emit("");
        }
    }

    @Module
    interface Init {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        default Emitter emitter() {
            return backend().emitter();
        }

        void prototype(AlgebraicType type);

        default void prototype(ProductType product) {
            emitter().emit("%s init_%s(%s);", backend().typeseval().type(product), backend().typeseval().type(product), product.getFields().stream().map(field -> backend().declarations().declaration(field.getType(), field.getName())).collect(Collectors.joining(", ")));
            emitter().emit("");
        }

        default void prototype(SumType sum) {
            sum.getVariants().forEach(variant -> {
                emitter().emit("%s init_%s_%s(%s);", backend().typeseval().type(sum), backend().typeseval().type(sum), utils().mangle(variant.getName()), variant.getFields().stream().map(field -> backend().declarations().declaration(field.getType(), field.getName())).collect(Collectors.joining(", ")));
                emitter().emit("");
            });
        }

        void definition(AlgebraicType type);

        default void definition(ProductType product) {
            String self = "self";
            emitter().emit("%s init_%s(%s) {", backend().typeseval().type(product), backend().typeseval().type(product), product.getFields().stream().map(field -> backend().declarations().declaration(field.getType(), field.getName())).collect(Collectors.joining(", ")));
            emitter().increaseIndentation();
            emitter().emit("%s = calloc(1, sizeof(%s));", backend().declarations().declaration(product, self), utils().internalName(product));
            emitter().emit("if (!%s) return NULL;", self);
            product.getFields().forEach(field -> backend().statements().copy(field.getType(), String.format("%s->%s", self, field.getName()), field.getType(), field.getName()));
            emitter().emit("return %s;", self);
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }

        default void definition(SumType sum) {
            sum.getVariants().forEach(variant -> {
                String self = "self";
                emitter().emit("%s init_%s_%s(%s) {", backend().typeseval().type(sum), backend().typeseval().type(sum), utils().mangle(variant.getName()), variant.getFields().stream().map(field -> backend().declarations().declaration(field.getType(), field.getName())).collect(Collectors.joining(", ")));
                emitter().increaseIndentation();
                emitter().emit("%s = calloc(1, sizeof(%s));", backend().declarations().declaration(sum, self), utils().internalName(sum));
                emitter().emit("if (!%s) return NULL;", self);
                emitter().emit("%s->tag = tag_%s_%s;", self, utils().mangle(sum.getName()), utils().mangle(variant.getName()));
                variant.getFields().forEach(field -> {
                    backend().statements().copy(field.getType(), String.format("%s->data.%s.%s", self, utils().mangle(variant.getName()), field.getName()), field.getType(), field.getName());
                });
                emitter().emit("return %s;", self);
                emitter().decreaseIndentation();
                emitter().emit("}");
                emitter().emit("");
            });
        }
    }

    @Module
    interface Free {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        @Binding(BindingKind.INJECTED)
        Alias alias();

        @Binding(BindingKind.INJECTED)
        Emitter emitter();

        void prototype(AlgebraicType type);

        default void prototype(ProductType product) {
            emitter().emit("void free_%s(%s);", backend().typeseval().type(product), backend().declarations().declaration(product, "self"));
            emitter().emit("");
        }

        default void prototype(SumType sum) {
            emitter().emit("void free_%s(%s);", backend().typeseval().type(sum), backend().declarations().declaration(sum, "self"));
            emitter().emit("");
        }

        void definition(AlgebraicType type);

        default void definition(ProductType product) {
            String self = "self";
            emitter().emit("void free_%s(%s) {", backend().typeseval().type(product), backend().declarations().declaration(product, self));
            emitter().increaseIndentation();
            emitter().emit("if (!%s) return;", self);
            product.getFields().forEach(field -> {
                backend().free().apply(field.getType(), String.format("%s->%s", self, field.getName()));
            });
            emitter().emit("free(%s);", self);
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }

        default void definition(SumType sum) {
            String self = "self";
            emitter().emit("void free_%s(%s) {", backend().typeseval().type(sum), backend().declarations().declaration(sum, self));
            emitter().increaseIndentation();
            emitter().emit("if (!%s) return;", self);
            emitter().emit("for (enum %s_tag_t tag = tag_%s_%s; tag <= tag_%s_%s; ++tag) {", utils().mangle(sum.getName()), utils().mangle(sum.getName()), utils().mangle(sum.getVariants().get(0).getName()), utils().mangle(sum.getName()), utils().mangle(sum.getVariants().get(sum.getVariants().size() - 1).getName()));
            emitter().increaseIndentation();
            emitter().emit("switch (tag) {");
            emitter().increaseIndentation();
            sum.getVariants().forEach(variant -> {
                emitter().emit("case tag_%s_%s:", utils().mangle(sum.getName()), utils().mangle(variant.getName()));
                emitter().increaseIndentation();
                variant.getFields().forEach(field -> {
                    backend().free().apply(field.getType(), String.format("%s->data.%s.%s", self, utils().mangle(variant.getName()), field.getName()));
                });
                emitter().emit("break;");
                emitter().decreaseIndentation();
            });
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("free(%s);", self);
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Write {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Alias alias();

        @Binding(BindingKind.INJECTED)
        Emitter emitter();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        @Binding(BindingKind.INJECTED)
        Serialization serialization();

        void prototype(AlgebraicType type);

        default void prototype(ProductType product) {
            emitter().emit("void write_%s(%s, char *buffer);", backend().typeseval().type(product), backend().declarations().declaration(product, "self"));
            emitter().emit("");
        }

        default void prototype(SumType sum) {
            emitter().emit("void write_%s(%s, char *buffer);", backend().typeseval().type(sum), backend().declarations().declaration(sum, "self"));
            emitter().emit("");
        }

        void definition(AlgebraicType type);

        default void definition(ProductType product) {
            String self = "self";
            String ptr = "ptr";
            emitter().emit("void write_%s(%s, char *buffer) {", backend().typeseval().type(product), backend().declarations().declaration(product, self));
            emitter().increaseIndentation();
            emitter().emit("if (!%s || !buffer) return;", self);
            emitter().emit("char *%s = buffer;", ptr);
            product.getFields().forEach(field -> {
                serialization().write(field.getType(), String.format("%s->%s", self, field.getName()), ptr);
            });
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }

        default void definition(SumType sum) {
            String self = "self";
            String ptr = "ptr";
            emitter().emit("void write_%s(%s, char *buffer) {", backend().typeseval().type(sum), backend().declarations().declaration(sum, self));
            emitter().increaseIndentation();
            emitter().emit("if (!%s || !buffer) return;", self);
            emitter().emit("char *%s = buffer;", ptr);
            emitter().emit("*(enum %s_tag_t*) %s = %s->tag;", utils().mangle(sum.getName()), ptr, self);
            emitter().emit("%s = (char*)((enum %s_tag_t*) ptr + 1);", ptr, utils().mangle(sum.getName()), ptr);
            emitter().emit("switch (%s->tag) {", self);
            emitter().increaseIndentation();
            sum.getVariants().forEach(variant -> {
                emitter().emit("case tag_%s_%s:", utils().mangle(sum.getName()), utils().mangle(variant.getName()));
                emitter().increaseIndentation();
                variant.getFields().forEach(field -> {
                    serialization().write(field.getType(), String.format("%s->data.%s.%s", self, utils().mangle(variant.getName()), field.getName()), ptr);
                });
                emitter().emit("break;");
                emitter().decreaseIndentation();
            });
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Read {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Alias alias();

        @Binding(BindingKind.INJECTED)
        Emitter emitter();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        @Binding(BindingKind.INJECTED)
        Serialization serialization();

        void prototype(AlgebraicType type);

        default void prototype(ProductType product) {
            emitter().emit("%s read_%s(char *buffer);", backend().typeseval().type(product), backend().typeseval().type(product));
            emitter().emit("");
        }

        default void prototype(SumType sum) {
            emitter().emit("%s read_%s(char *buffer);", backend().typeseval().type(sum), backend().typeseval().type(sum));
            emitter().emit("");
        }

        void definition(AlgebraicType type);

        default void definition(ProductType product) {
            String self = "self";
            String ptr = "ptr";
            emitter().emit("%s read_%s(char *buffer) {", backend().typeseval().type(product), backend().typeseval().type(product));
            emitter().increaseIndentation();
            emitter().emit("if (!buffer) return NULL;");
            emitter().emit("char *%s = buffer;", ptr);
            emitter().emit("%s = calloc(1, sizeof(%s));", backend().declarations().declaration(product, self), utils().internalName(product));
            emitter().emit("if (!%s) return NULL;", self);
            product.getFields().forEach(field -> {
                serialization().read(field.getType(), String.format("%s->%s", self, field.getName()), ptr);
            });
            emitter().emit("return %s;", self);
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }

        default void definition(SumType sum) {
            String self = "self";
            String ptr = "ptr";
            emitter().emit("%s read_%s(char *buffer) {", backend().typeseval().type(sum), backend().typeseval().type(sum));
            emitter().increaseIndentation();
            emitter().emit("if (!buffer) return NULL;");
            emitter().emit("char *%s = buffer;", ptr);
            emitter().emit("%s = calloc(1, sizeof(%s));", backend().declarations().declaration(sum, self), utils().internalName(sum));
            emitter().emit("if (!%s) return NULL;", self);
            emitter().emit("%s->tag = *(enum %s_tag_t*) %s;", self, utils().mangle(sum.getName()), ptr);
            emitter().emit("%s = (char*)((enum %s_tag_t*) ptr + 1);", ptr, utils().mangle(sum.getName()), ptr);
            emitter().emit("switch (%s->tag) {", self);
            emitter().increaseIndentation();
            sum.getVariants().forEach(variant -> {
                emitter().emit("case tag_%s_%s:", utils().mangle(sum.getName()), utils().mangle(variant.getName()));
                emitter().increaseIndentation();
                variant.getFields().forEach(field -> {
                    serialization().read(field.getType(), String.format("%s->data.%s.%s", self, utils().mangle(variant.getName()), field.getName()), ptr);
                });
                emitter().emit("break;");
                emitter().decreaseIndentation();
            });
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("return self;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Size {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Alias alias();

        @Binding(BindingKind.INJECTED)
        Emitter emitter();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        @Binding(BindingKind.INJECTED)
        SizeOf sizeof();

        void prototype(AlgebraicType type);

        default void prototype(ProductType product) {
            emitter().emit("size_t size_%s(%s);", backend().typeseval().type(product), backend().declarations().declaration(product, "self"));
            emitter().emit("");
        }

        default void prototype(SumType sum) {
            emitter().emit("size_t size_%s(%s);", backend().typeseval().type(sum), backend().declarations().declaration(sum, "self"));
            emitter().emit("");
        }

        void definition(AlgebraicType type);

        default void definition(ProductType product) {
            String self = "self";
            String size = "size";
            emitter().emit("size_t size_%s(%s) {", backend().typeseval().type(product), backend().declarations().declaration(product, self));
            emitter().increaseIndentation();
            emitter().emit("if (!%s) return 0;", self);
            emitter().emit("size_t %s = 0;", size);
            product.getFields().forEach(field -> {
                emitter().emit("%s += %s;", size, sizeof().evaluate(field.getType(), String.format("%s->%s", self, field.getName())));
            });
            emitter().emit("return %s;", size);
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }

        default void definition(SumType sum) {
            String self = "self";
            String size = "size";
            emitter().emit("size_t size_%s(%s) {", backend().typeseval().type(sum), backend().declarations().declaration(sum, self));
            emitter().increaseIndentation();
            emitter().emit("if (!%s) return 0;", self);
            emitter().emit("size_t %s = 0;", size);
            emitter().emit("%s += sizeof(enum %s_tag_t);", size, utils().mangle(sum.getName()));
            emitter().emit("switch (%s->tag) {", self);
            emitter().increaseIndentation();
            sum.getVariants().forEach(variant -> {
                emitter().emit("case tag_%s_%s:", utils().mangle(sum.getName()), utils().mangle(variant.getName()));
                emitter().increaseIndentation();
                variant.getFields().forEach(field -> {
                    emitter().emit("%s += %s;", size, sizeof().evaluate(field.getType(), String.format("%s->data.%s.%s", self, utils().mangle(variant.getName()), field.getName())));
                });
                emitter().emit("break;");
                emitter().decreaseIndentation();
            });
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("return %s;", size);
            emitter().decreaseIndentation();
            ;
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Copy {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        @Binding(BindingKind.INJECTED)
        Alias alias();

        @Binding(BindingKind.INJECTED)
        Emitter emitter();

        void prototype(AlgebraicType type);

        default void prototype(ProductType product) {
            emitter().emit("void copy_%s(%s, %s);", backend().typeseval().type(product), backend().declarations().declaration(product, "*to"), backend().declarations().declaration(product, "from"));
            emitter().emit("");
        }

        default void prototype(SumType sum) {
            emitter().emit("void copy_%s(%s, %s);", backend().typeseval().type(sum), backend().declarations().declaration(sum, "*to"), backend().declarations().declaration(sum, "from"));
            emitter().emit("");
        }

        void definition(AlgebraicType type);

        default void definition(ProductType product) {
            String from = "from";
            String to = "to";
            emitter().emit("void copy_%s(%s, %s) {", backend().typeseval().type(product), backend().declarations().declaration(product, "*" + to), backend().declarations().declaration(product, from));
            emitter().increaseIndentation();
            emitter().emit("if (!%s || !%s) return;", to, from);
            emitter().emit("if (*%s == %s) return;", to, from);
            emitter().emit("if (*%s) { %s(*%s); *%s = NULL; }", to, utils().destructor(product), to, to);
            emitter().emit("if (!(*%s)) *%s = calloc(1, sizeof(%s));", to, to, utils().internalName(product));
            emitter().emit("if (!(*%s)) return;", to);
            product.getFields().forEach(field -> {
                backend().statements().copy(field.getType(), "(*" + to + ")->" + field.getName(), field.getType(), from + "->" + field.getName());
            });
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }

        default void definition(SumType sum) {
            String from = "from";
            String to = "to";
            emitter().emit("void copy_%s(%s, %s) {", backend().typeseval().type(sum), backend().declarations().declaration(sum, "*" + to), backend().declarations().declaration(sum, from));
            emitter().increaseIndentation();
            emitter().emit("if (!%s || !%s) return;", to, from);
            emitter().emit("if (*%s == %s) return;", to, from);
            emitter().emit("if (*%s) { %s(*%s); *%s = NULL; }", to, utils().destructor(sum), to, to);
            emitter().emit("if (!(*%s)) *%s = calloc(1, sizeof(%s));", to, to, utils().internalName(sum));
            emitter().emit("if (!(*%s)) return;", to);
            emitter().emit("(*%s)->tag = %s->tag;", to, from);
            emitter().emit("switch (%s->tag) {", from);
            emitter().increaseIndentation();
            sum.getVariants().forEach(variant -> {
                emitter().emit("case tag_%s_%s:", utils().mangle(sum.getName()), utils().mangle(variant.getName()));
                emitter().increaseIndentation();
                variant.getFields().forEach(field -> {
                    String field1 = String.format("->data.%s.%s", utils().mangle(variant.getName()), field.getName());
                    backend().statements().copy(field.getType(), "(*" + to + ")" + field1, field.getType(), from + field1);
                });
                emitter().emit("break;");
                emitter().decreaseIndentation();
            });
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Compare {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Alias alias();

        @Binding(BindingKind.INJECTED)
        Emitter emitter();

        @Binding(BindingKind.INJECTED)
        Utils utils();

        void prototype(AlgebraicType type);

        default void prototype(ProductType product) {
            emitter().emit("%s compare_%s(%s, %s);", backend().typeseval().type(BoolType.INSTANCE), backend().typeseval().type(product), backend().declarations().declaration(product, "lhs"), backend().declarations().declaration(product, "rhs"));
            emitter().emit("");
        }

        default void prototype(SumType sum) {
            emitter().emit("%s compare_%s(%s, %s);", BoolType.INSTANCE, backend().typeseval().type(sum), backend().declarations().declaration(sum, "lhs"), backend().declarations().declaration(sum, "rhs"));
            emitter().emit("");
        }

        void definition(AlgebraicType type);

        default void definition(ProductType product) {
            String lhs = "lhs";
            String rhs = "rhs";
            emitter().emit("%s compare_%s(%s, %s) {", backend().typeseval().type(BoolType.INSTANCE), backend().typeseval().type(product), backend().declarations().declaration(product, lhs), backend().declarations().declaration(product, rhs));
            emitter().increaseIndentation();
            emitter().emit("if (!%s || !%s) return false;", lhs, rhs);
            emitter().emit("if (%s == %s) return true;", lhs, rhs);
            product.getFields().forEach(field -> {
                emitter().emit("if (!%s) return false;", backend().expressionEval().compare(field.getType(), String.format("%s->%s", lhs, field.getName()), field.getType(), String.format("%s->%s", rhs, field.getName())));
            });
            emitter().emit("return true;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }

        default void definition(SumType sum) {
            String lhs = "lhs";
            String rhs = "rhs";
            emitter().emit("%s compare_%s(%s, %s) {", backend().typeseval().type(BoolType.INSTANCE), backend().typeseval().type(sum), backend().declarations().declaration(sum, "lhs"), backend().declarations().declaration(sum, "rhs"));
            emitter().increaseIndentation();
            emitter().emit("if (!%s || !%s) return false;", lhs, rhs);
            emitter().emit("if (%s == %s) return true;", lhs, rhs);
            emitter().emit("if (%s->tag != %s->tag) return false;", lhs, rhs);
            emitter().emit("switch (%s->tag) {", lhs);
            emitter().increaseIndentation();
            sum.getVariants().forEach(variant -> {
                emitter().emit("case tag_%s_%s: {", utils().mangle(sum.getName()), utils().mangle(variant.getName()));
                emitter().increaseIndentation();
                variant.getFields().forEach(field -> {
                    emitter().emit("if (!%s) return false;", backend().expressionEval().compare(field.getType(), String.format("%s->data.%s.%s", lhs, utils().mangle(variant.getName()), field.getName()), field.getType(), String.format("%s->data.%s.%s", rhs, utils().mangle(variant.getName()), field.getName())));
                });
                emitter().emit("break;");
                emitter().decreaseIndentation();
                emitter().emit("}");
            });
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("return true;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Utils {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        default String name(AlgebraicType type) {
            return mangle(type.getName()) + "_t";
        }

        default String internalName(AlgebraicType type) {
            return "struct " + mangle(type.getName()) + "_s";
        }

        default String mangle(String str) {
            return str.replaceAll("[(,) ]", "_");
        }

        default String constructor(String constructor) {
            return types()
                    .filter(AlgebraicType.class::isInstance)
                    .map(AlgebraicType.class::cast)
                    .filter(type -> {
                        if (type instanceof ProductType) {
                            return Objects.equals(type.getName(), constructor);
                        } else {
                            return ((SumType) type).getVariants().stream().anyMatch(variant -> Objects.equals(variant.getName(), constructor));
                        }
                    })
                    .map(type -> {
                        if (type instanceof ProductType) {
                            return "init_" + name(type);
                        } else {
                            return ((SumType) type).getVariants().stream().filter(variant -> Objects.equals(variant.getName(), constructor)).map(variant -> "init_" + name(type) + "_" + mangle(variant.getName())).findAny().get();
                        }
                    })
                    .findAny()
                    .get();
        }

        default String destructor(AlgebraicType type) {
            return String.format("free_%s", name(type));
        }

        default Stream<AlgebraicType> types() {
            return backend().task()
                    .getSourceUnits().stream()
                    .flatMap(unit -> unit.getTree().getTypeDecls().stream())
                    .filter(AlgebraicTypeDecl.class::isInstance)
                    .map(decl -> (AlgebraicType) backend().types().declaredGlobalType(decl));
        }
    }

}