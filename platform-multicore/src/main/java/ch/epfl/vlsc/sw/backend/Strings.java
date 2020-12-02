package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.CharType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.RealType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.Type;

import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.multij.BindingKind.LAZY;

@Module
public interface Strings {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

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
                .bind("concat").to(concat())
                .bind("membership").to(membership())
                .bind("lessThan").to(lessThan())
                .bind("lessThanEqual").to(lessThanEqual())
                .bind("greaterThan").to(greaterThan())
                .bind("greaterThanEqual").to(greaterThanEqual())
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
                .bind("concat").to(concat())
                .bind("membership").to(membership())
                .bind("lessThan").to(lessThan())
                .bind("lessThanEqual").to(lessThanEqual())
                .bind("greaterThan").to(greaterThan())
                .bind("greaterThanEqual").to(greaterThanEqual())
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
                .instance();
    }

    @Binding(LAZY)
    default Read read() {
        return MultiJ.from(Read.class)
                .bind("backend").to(backend())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Size size() {
        return MultiJ.from(Size.class)
                .bind("backend").to(backend())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
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
                .bind("code").to(backend().typeseval())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Concat concat() {
        return MultiJ.from(Concat.class)
                .bind("backend").to(backend())
                .bind("code").to(backend().typeseval())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default Membership membership() {
        return MultiJ.from(Membership.class)
                .bind("backend").to(backend())
                .bind("code").to(backend().typeseval())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default LessThan lessThan() {
        return MultiJ.from(LessThan.class)
                .bind("backend").to(backend())
                .bind("code").to(backend().typeseval())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default LessThanEqual lessThanEqual() {
        return MultiJ.from(LessThanEqual.class)
                .bind("backend").to(backend())
                .bind("code").to(backend().typeseval())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default GreaterThan greaterThan() {
        return MultiJ.from(GreaterThan.class)
                .bind("backend").to(backend())
                .bind("code").to(backend().typeseval())
                .bind("alias").to(backend().alias())
                .bind("emitter").to(backend().emitter())
                .bind("utils").to(utils())
                .instance();
    }

    @Binding(LAZY)
    default GreaterThanEqual greaterThanEqual() {
        return MultiJ.from(GreaterThanEqual.class)
                .bind("backend").to(backend())
                .bind("code").to(backend().typeseval())
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

    default void declareString() {
        backend().emitter().emit("// STRING DECLARATION");
        utils().types().forEach(type -> {
            prototypes().apply(type);
        });
    }

    default void defineString() {
        backend().emitter().emit("// STRING DEFINITION");
        utils().types().forEach(type -> {
            definitions().apply(type);
        });
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
        @Binding(BindingKind.INJECTED)
        Concat concat();
        @Binding(BindingKind.INJECTED)
        Membership membership();
        @Binding(BindingKind.INJECTED)
        LessThan lessThan();
        @Binding(BindingKind.INJECTED)
        LessThanEqual lessThanEqual();
        @Binding(BindingKind.INJECTED)
        GreaterThan greaterThan();
        @Binding(BindingKind.INJECTED)
        GreaterThanEqual greaterThanEqual();

        default void apply(StringType type) {
            typedef().apply(type);
            init().prototype(type);
            free().prototype(type);
            write().prototype(type);
            read().prototype(type);
            size().prototype(type);
            copy().prototype(type);
            compare().prototype(type);
            concat().prototype(type);
            membership().prototype(type);
            lessThan().prototype(type);
            lessThanEqual().prototype(type);
            greaterThan().prototype(type);
            greaterThanEqual().prototype(type);
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
        @Binding(BindingKind.INJECTED)
        Concat concat();
        @Binding(BindingKind.INJECTED)
        Membership membership();
        @Binding(BindingKind.INJECTED)
        LessThan lessThan();
        @Binding(BindingKind.INJECTED)
        LessThanEqual lessThanEqual();
        @Binding(BindingKind.INJECTED)
        GreaterThan greaterThan();
        @Binding(BindingKind.INJECTED)
        GreaterThanEqual greaterThanEqual();

        default void apply(StringType type) {
            init().definition(type);
            free().definition(type);
            write().definition(type);
            read().definition(type);
            size().definition(type);
            copy().definition(type);
            compare().definition(type);
            concat().definition(type);
            membership().definition(type);
            lessThan().definition(type);
            lessThanEqual().definition(type);
            greaterThan().definition(type);
            greaterThanEqual().definition(type);
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

        default void apply(StringType type) {
            emitter().emit("typedef char* %s;", utils().name(type));
            emitter().emit("");
        }
    }

    @Module
    interface Init {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        @Binding(BindingKind.INJECTED)
        Alias alias();
        @Binding(BindingKind.INJECTED)
        Emitter emitter();
        @Binding(BindingKind.INJECTED)
        Utils utils();

        default void prototype(StringType type) {
            emitter().emit("extern %1$s init_%1$s(const char* str);", utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%1$s init_%1$s(const char* str) {", utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("if (str == NULL) return NULL;");
            emitter().emit("%1$s self = static_cast<string_t>(calloc(1, strlen(str) + 1));", utils().name(type));
            emitter().emit("if (self == NULL) return NULL;");
            emitter().emit("strcpy(self, str);");
            emitter().emit("return self;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
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

        default void prototype(StringType type) {
            emitter().emit("extern void free_%1$s(%1$s self);", utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("void free_%1$s(%1$s self) {", utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("free(self);");
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

        default void prototype(StringType type) {
            emitter().emit("extern void write_%1$s(const %1$s self, char* buffer);", utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("void write_%1$s(const %1$s self, char* buffer) {", utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("if (self == NULL || buffer == NULL) return;");
            emitter().emit("char* ptr = buffer;");
            emitter().emit("*(size_t*) ptr =  strlen(self);");
            emitter().emit("ptr = (char*)((size_t*) ptr + 1);");
            emitter().emit("strcpy(ptr, self);");
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

        default void prototype(StringType type) {
            emitter().emit("extern %s read_%1$s(char* buffer);", utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%s read_%1$s(char* buffer) {", utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("if (buffer == NULL) return NULL;");
            emitter().emit("char* ptr = buffer;");
            emitter().emit("size_t length = *(size_t*) ptr;");
            emitter().emit("ptr = (char*)((size_t*) ptr + 1);");
            emitter().emit("%1$s result = static_cast<string_t>(calloc(1, length + 1));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strncpy(result, ptr, length);");
            emitter().emit("return result;");
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

        default void prototype(StringType type) {
            emitter().emit("extern size_t size_%1$s(const %1$s self);", utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("size_t size_%1$s(const %1$s self) {", utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("if (self == NULL) return 0;");
            emitter().emit("return sizeof(size_t) + strlen(self);");
            emitter().decreaseIndentation();
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

        default void prototype(StringType type) {
            emitter().emit("extern void copy_%1$s(%1$s* lhs, const %1$s rhs);", utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("void copy_%1$s(%1$s* lhs, const %1$s rhs) {", utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL || rhs == NULL) return;");
            emitter().emit("%s tmp = static_cast<string_t>(realloc(*lhs, sizeof(char) * (strlen(rhs) + 1)));", utils().name(type));
            emitter().emit("if (tmp == NULL) return;");
            emitter().emit("*lhs = tmp;");
            emitter().emit("strcpy(*lhs, rhs);");
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
        TypesEvaluator code();
        @Binding(BindingKind.INJECTED)
        Alias alias();
        @Binding(BindingKind.INJECTED)
        Emitter emitter();
        @Binding(BindingKind.INJECTED)
        Utils utils();

        default void prototype(StringType type) {
            emitter().emit("extern %1$s compare_%2$s(const %2$s lhs, const %2$s rhs);", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%1$s compare_%2$s(const %2$s lhs, const %2$s rhs) {", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL && rhs == NULL) return true;");
            emitter().emit("if (lhs == NULL || rhs == NULL) return false;");
            emitter().emit("return strcmp(lhs, rhs) == 0;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Concat {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();
        @Binding(BindingKind.INJECTED)
        TypesEvaluator code();
        @Binding(BindingKind.INJECTED)
        Alias alias();
        @Binding(BindingKind.INJECTED)
        Emitter emitter();
        @Binding(BindingKind.INJECTED)
        Utils utils();

        default void prototype(StringType type) {
            emitter().emit("extern %1$s concat_%1$s_%1$s(const %1$s lhs, const %1$s rhs);", utils().name(type));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs);", utils().name(type), code().type(CharType.INSTANCE));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs);", utils().name(type), code().type(CharType.INSTANCE));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs);", utils().name(type), code().type(BoolType.INSTANCE));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs);", utils().name(type), code().type(BoolType.INSTANCE));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs);", utils().name(type), code().type(RealType.f64));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs);", utils().name(type), code().type(RealType.f64));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs);", utils().name(type), code().type(new IntType(OptionalInt.empty(), true)));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs);", utils().name(type), code().type(new IntType(OptionalInt.empty(), true)));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs);", utils().name(type), code().type(new IntType(OptionalInt.empty(), false)));
            emitter().emit("");

            emitter().emit("extern %1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs);", utils().name(type), code().type(new IntType(OptionalInt.empty(), false)));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%1$s concat_%1$s_%1$s(const %1$s lhs, const %1$s rhs) {", utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL || rhs == NULL) return NULL;");
            emitter().emit("size_t lhs_length = strlen(lhs);");
            emitter().emit("size_t rhs_length = strlen(rhs);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, lhs_length + rhs_length + 1));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcat(result, lhs);");
            emitter().emit("strcat(result + lhs_length, rhs);");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs) {", utils().name(type), code().type(CharType.INSTANCE));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL) return NULL;");
            emitter().emit("size_t length = strlen(lhs);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, length + 2));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, lhs);");
            emitter().emit("result[length] = rhs;");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs) {", utils().name(type), code().type(CharType.INSTANCE));
            emitter().increaseIndentation();
            emitter().emit("if (rhs == NULL) return NULL;");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, strlen(rhs) + 2));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result + 1, rhs);");
            emitter().emit("result[0] = lhs;");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs) {", utils().name(type), code().type(BoolType.INSTANCE));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL) return NULL;");
            emitter().emit("size_t length = strlen(lhs);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, length + 1 + (rhs ? 4 : 5)));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, lhs);");
            emitter().emit("strcpy(result + length, rhs ? \"true\" : \"false\");");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs) {", utils().name(type), code().type(BoolType.INSTANCE));
            emitter().increaseIndentation();
            emitter().emit("if (rhs == NULL) return NULL;");
            emitter().emit("size_t length = rhs ? 4 : 5;");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, strlen(rhs) + 1 + length));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, lhs ? \"true\" : \"false\");");
            emitter().emit("strcpy(result + length, rhs);");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs) {", utils().name(type), code().type(RealType.f64));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL) return NULL;");
            emitter().emit("char buffer[256] = {0};");
            emitter().emit("sprintf(buffer, \"%%g\", rhs);");
            emitter().emit("size_t str_len = strlen(lhs);");
            emitter().emit("size_t buf_len = strlen(buffer);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, str_len + buf_len + 1));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, lhs);");
            emitter().emit("strcpy(result + str_len, buffer);");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs) {", utils().name(type), code().type(RealType.f64));
            emitter().increaseIndentation();
            emitter().emit("if (rhs == NULL) return NULL;");
            emitter().emit("char buffer[256] = {0};");
            emitter().emit("sprintf(buffer, \"%%g\", lhs);");
            emitter().emit("size_t buf_len = strlen(buffer);");
            emitter().emit("size_t str_len = strlen(rhs);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, buf_len + str_len  + 1));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, buffer);");
            emitter().emit("strcpy(result + buf_len, rhs);");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs) {", utils().name(type), code().type(new IntType(OptionalInt.empty(), true)));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL) return NULL;");
            emitter().emit("char buffer[256] = {0};");
            emitter().emit("sprintf(buffer, \"%%d\", rhs);");
            emitter().emit("size_t str_len = strlen(lhs);");
            emitter().emit("size_t buf_len = strlen(buffer);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, str_len + buf_len + 1));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, lhs);");
            emitter().emit("strcpy(result + str_len, buffer);");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs) {", utils().name(type), code().type(new IntType(OptionalInt.empty(), true)));
            emitter().increaseIndentation();
            emitter().emit("if (rhs == NULL) return NULL;");
            emitter().emit("char buffer[256] = {0};");
            emitter().emit("sprintf(buffer, \"%%d\", lhs);");
            emitter().emit("size_t buf_len = strlen(buffer);");
            emitter().emit("size_t str_len = strlen(rhs);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, buf_len + str_len  + 1));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, buffer);");
            emitter().emit("strcpy(result + buf_len, rhs);");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%1$s_%2$s(const %1$s lhs, const %2$s rhs) {", utils().name(type), code().type(new IntType(OptionalInt.empty(), false)));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL) return NULL;");
            emitter().emit("char buffer[256] = {0};");
            emitter().emit("sprintf(buffer, \"%%u\", rhs);");
            emitter().emit("size_t str_len = strlen(lhs);");
            emitter().emit("size_t buf_len = strlen(buffer);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, str_len + buf_len + 1));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, lhs);");
            emitter().emit("strcpy(result + str_len, buffer);");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");

            emitter().emit("%1$s concat_%2$s_%1$s(const %2$s lhs, const %1$s rhs) {", utils().name(type), code().type(new IntType(OptionalInt.empty(), false)));
            emitter().increaseIndentation();
            emitter().emit("if (rhs == NULL) return NULL;");
            emitter().emit("char buffer[256] = {0};");
            emitter().emit("sprintf(buffer, \"%%u\", lhs);");
            emitter().emit("size_t buf_len = strlen(buffer);");
            emitter().emit("size_t str_len = strlen(rhs);");
            emitter().emit("%s result = static_cast<string_t>(calloc(1, buf_len + str_len  + 1));", utils().name(type));
            emitter().emit("if (result == NULL) return NULL;");
            emitter().emit("strcpy(result, buffer);");
            emitter().emit("strcpy(result + buf_len, rhs);");
            emitter().emit("return result;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Membership {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();
        @Binding(BindingKind.INJECTED)
        TypesEvaluator code();
        @Binding(BindingKind.INJECTED)
        Alias alias();
        @Binding(BindingKind.INJECTED)
        Emitter emitter();
        @Binding(BindingKind.INJECTED)
        Utils utils();

        default void prototype(StringType type) {
            emitter().emit("extern %1$s membership_%2$s(const %2$s self, %3$s elem);", code().type(BoolType.INSTANCE), utils().name(type), code().type(CharType.INSTANCE));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%1$s membership_%2$s(const %2$s self, %3$s elem) {", code().type(BoolType.INSTANCE), utils().name(type), code().type(CharType.INSTANCE));
            emitter().increaseIndentation();
            emitter().emit("if (self == NULL) return false;");
            emitter().emit("return strchr(self, elem) != NULL;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface LessThan {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();
        @Binding(BindingKind.INJECTED)
        TypesEvaluator code();
        @Binding(BindingKind.INJECTED)
        Alias alias();
        @Binding(BindingKind.INJECTED)
        Emitter emitter();
        @Binding(BindingKind.INJECTED)
        Utils utils();

        default void prototype(StringType type) {
            emitter().emit("extern %1$s less_than_%2$s(const %2$s lhs, const %2$s rhs);", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%1$s less_than_%2$s(const %2$s lhs, const %2$s rhs) {", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("if (lhs == NULL || rhs == NULL) return false;");
            emitter().emit("return strcmp(lhs, rhs) < 0;");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface LessThanEqual {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();
        @Binding(BindingKind.INJECTED)
        TypesEvaluator code();
        @Binding(BindingKind.INJECTED)
        Alias alias();
        @Binding(BindingKind.INJECTED)
        Emitter emitter();
        @Binding(BindingKind.INJECTED)
        Utils utils();

        default void prototype(StringType type) {
            emitter().emit("extern %1$s less_than_equal_%2$s(const %2$s lhs, const %2$s rhs);", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%1$s less_than_equal_%2$s(const %2$s lhs, const %2$s rhs) {", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("return less_than_%1$s(lhs, rhs) || compare_%1$s(lhs, rhs);", utils().name(type));
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface GreaterThan {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();
        @Binding(BindingKind.INJECTED)
        TypesEvaluator code();
        @Binding(BindingKind.INJECTED)
        Alias alias();
        @Binding(BindingKind.INJECTED)
        Emitter emitter();
        @Binding(BindingKind.INJECTED)
        Utils utils();

        default void prototype(StringType type) {
            emitter().emit("extern %1$s greater_than_%2$s(const %2$s lhs, const %2$s rhs);", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%1$s greater_than_%2$s(const %2$s lhs, const %2$s rhs) {", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("return !(less_than_equal_%1$s(lhs, rhs));", utils().name(type));
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface GreaterThanEqual {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();
        @Binding(BindingKind.INJECTED)
        TypesEvaluator code();
        @Binding(BindingKind.INJECTED)
        Alias alias();
        @Binding(BindingKind.INJECTED)
        Emitter emitter();
        @Binding(BindingKind.INJECTED)
        Utils utils();

        default void prototype(StringType type) {
            emitter().emit("extern %1$s greater_than_equal_%2$s(const %2$s lhs, const %2$s rhs);", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().emit("");
        }

        default void definition(StringType type) {
            emitter().emit("%1$s greater_than_equal_%2$s(const %2$s lhs, const %2$s rhs) {", code().type(BoolType.INSTANCE), utils().name(type));
            emitter().increaseIndentation();
            emitter().emit("return !(less_than_%1$s(lhs, rhs));", utils().name(type));
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
        }
    }

    @Module
    interface Utils {

        @Binding(BindingKind.INJECTED)
        MulticoreBackend backend();

        default String name(StringType type) {
            return backend().typeseval().type(type);
        }

        default Stream<StringType> types() {
            return backend().task().walk()
                    .flatMap(this::type)
                    .distinct();
        }

        default Stream<StringType> type(IRNode node) {
            return Stream.empty();
        }

        default Stream<StringType> type(VarDecl decl) {
            return wrapIfString(backend().types().declaredType(decl));
        }
        default Stream<StringType> type(Expression expr) {
            Type t = backend().types().type(expr);
            return wrapIfString(t);
        }

        default Stream<StringType> wrapIfString(Type t) {
            return Stream.empty();
        }

        default Stream<StringType> wrapIfString(StringType t) {
            return Stream.of(t);
        }
    }
}