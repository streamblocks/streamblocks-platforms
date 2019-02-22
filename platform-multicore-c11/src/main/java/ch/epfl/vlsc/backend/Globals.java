package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

@Module
public interface Globals {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void globalSource() {
        Path globalSourcePath = PathUtils.getTargetCodeGenSource(backend().context()).resolve("globals.c");
        emitter().open(globalSourcePath);

        backend().includeUser("globals.h");

        emitter().emit("// -- Define Callables");
        backend().callables().defineCallables();
        emitter().emit("// -- Glabal Variable Initilaization");
        globalVariableInitializer(getGlobalVarDecls());

        emitter().close();


    }

    default void globalHeader() {
        Path globalSourcePath = PathUtils.getTargetCodeGenInclude(backend().context()).resolve("globals.h");
        emitter().open(globalSourcePath);

        emitter().emit("#ifndef __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
        emitter().emit("#define __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
        emitter().emitNewLine();

        // -- Headers
        backend().includeSystem("stdint.h");
        backend().includeSystem("stdlib.h");
        backend().includeSystem("string.h");
        backend().includeUser("actors-rts.h");
        backend().includeUser("natives.h");
        emitter().emitNewLine();

        // -- Defines
        emitter().emit("#define TRUE 1");
        emitter().emit("#define FALSE 0");
        emitter().emit("#define TYPE_DIRECT");
        emitter().emitNewLine();

        // -- For now only max 4 dim and no optimization of struct size, {1,2,3,4,256};
        // list all dimensions needed, if someone wants more than 4 dimensions they get 256 since they anyway don't care about memory usage
        int dimensions[] = {4};
        /*
         * Print a type used to specify index or size
         * of an array. Used by the array copy methods.
         */
        emitter().emit("typedef struct {");
        emitter().increaseIndentation();
        emitter().emit("int32_t len;");
        emitter().emit("int32_t sz[%d];", dimensions[dimensions.length - 1]);
        emitter().decreaseIndentation();
        emitter().emit("} __arrayArg;");
        emitter().emitNewLine();

        /*
         * Print a function used to calc a max of
         * each dimension.
         * targetSz: the assigned target's size
         * exprSz: the assigning expression's size
         * shift: how many dimensions (due to index) that is used from the targetSz.
         */

        emitter().emit("static inline __arrayArg maxArraySz(__arrayArg *targetSz, __arrayArg *exprSz, int shift) {");
        emitter().increaseIndentation();
        emitter().emit("__arrayArg ret;");
        //Full array replace
        emitter().emit("if (shift == 0) {");
        emitter().increaseIndentation();
        emitter().emit("return *exprSz;");
        emitter().decreaseIndentation();
        emitter().emit("}");
        //Partial array replace shift = nbr of indices
        emitter().emit("ret.len = targetSz->len;");
        emitter().emit("memcpy(ret.sz, targetSz->sz, sizeof(int32_t) * 4);");
        emitter().emit("for (int i = 0; i < exprSz->len; i++) {");
        emitter().increaseIndentation();
        emitter().emit("if (targetSz->sz[i + shift] < exprSz->sz[i])");
        emitter().increaseIndentation();
        emitter().emit("ret.sz[i + shift] = exprSz->sz[i];");
        emitter().decreaseIndentation();
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("return ret;");
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();

        /*
         * These are the array types
         * typedef struct {
         *     union {
         *         T_t* p;           pointer to array of builtin typed elements of the flattened array
         *         T_t* (*pp);       pointer to array of pointers to user type elements of the flattened array
         *     };
         *     union { struct {
         *         uint16_t flags;  flags see below
         *         uint16_t dim;    dimensions valid in size array below
         *     }; uint32_t flags_dim;};
         *     int32_t sz[4];       size of each dimension (currently maximum of 4 allowed)
         * } __array4T_t;           This metadata type is created for all the builtin types and all the user types
         *
         * Flag field:
         *   Flags (true/false):
         *   0:0x01 direct(p)/indirect(pp)  (FIXME not set correctly in all the code, remove it since anyway linked to user type?)
         *   1:0x02 currently allocated(sz & pointer correct)/not-allocated (Very important to be correct)
         *   2:0x04 on heap/on stack, (the data is currently mostly allocated on the heap and if the pointer is changed the old needs to be (deep) freed (we know non-other keeps pointers to lower levels due to copy semantics)
         *   3:0x08 codegen (temporary created variable due to transformations go ahead and steel the memory)/cal variable (must obey copy semantics)
         *   4:0x10 part of multi-dim (can't change the pointer or free etc, since pointing into the memory of a larger array)/the full array
         */


        //Print array metadata struct and array methods for all builtin types
        for (String t : Arrays.asList("char", "int8_t", "uint8_t", "int16_t", "uint16_t", "int32_t", "uint32_t", "float", "double", "void", "_Bool")) {
            for (int i : dimensions) {
                emitter().emit("typedef struct {");
                emitter().increaseIndentation();
                emitter().emit("union {");
                emitter().increaseIndentation();
                emitter().emit("%s *p;", t);
                emitter().emit("%s *(*pp);", t);
                emitter().decreaseIndentation();
                emitter().emit("};");
                emitter().emit("union {");
                emitter().increaseIndentation();
                emitter().emit("struct {");
                emitter().increaseIndentation();
                emitter().emit("uint16_t flags;");
                emitter().emit("uint16_t dim;");
                emitter().decreaseIndentation();
                emitter().emit("};");
                emitter().emit("uint32_t flags_dim;");
                emitter().decreaseIndentation();
                emitter().emit("};");
                emitter().emit("int32_t sz[%d];", i);
                emitter().decreaseIndentation();
                emitter().emit("} __array%d%s;", i, t);
                emitter().emitNewLine();
            }
            emitter().emit("#define TYPE " + t);
            emitter().emitNewLine();
            emitter().emit("#include \"__arrayCopy.h\"");
            emitter().emitNewLine();
        }


        emitter().emit("#undef TYPE_DIRECT");
        emitter().emitNewLine();

        emitter().emit("// -- Declare Callables");
        backend().callables().declareCallables();
        emitter().emitNewLine();
        emitter().emit("// -- Declare Environment for Callables In Scope ");
        backend().callables().declareEnvironmentForCallablesInScope(backend().task());
        emitter().emit(" // -- Global Variables Declaration");
        emitter().emit("void init_global_variables(void);");
        globalVariableDeclarations(getGlobalVarDecls());
        emitter().emitNewLine();
        emitter().emit("#endif // __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());



        emitter().close();
    }

    default Stream<VarDecl> getGlobalVarDecls() {
        return backend().task()
                .getSourceUnits().stream()
                .flatMap(unit -> unit.getTree().getVarDecls().stream());
    }

    default void globalVariableDeclarations(Stream<VarDecl> varDecls) {
        varDecls.forEach(decl -> {
            Type type = backend().types().declaredType(decl);
            String d = backend().declarations().declaration(type, backend().variables().declarationName(decl));
            emitter().emit("%s;", d);
        });
    }

    default void globalVariableInitializer(Stream<VarDecl> varDecls) {
        emitter().emit("void init_global_variables() {");
        emitter().increaseIndentation();
        varDecls.forEach(decl -> {
            Type type = backend().types().declaredType(decl);
            if (decl.isExternal() && type instanceof CallableType) {
                String wrapperName = backend().callables().externalWrapperFunctionName(decl);
                String variableName = backend().variables().declarationName(decl);
                String t = backend().callables().mangle(type).encode();
                emitter().emit("%s = (%s) { *%s, NULL };", variableName, t, wrapperName);
            } else {
                backend().statements().copy(type, backend().variables().declarationName(decl), backend().types().type(decl.getValue()), backend().expressioneval().evaluate(decl.getValue()));
            }
        });
        emitter().decreaseIndentation();
        emitter().emit("}");
    }


}
