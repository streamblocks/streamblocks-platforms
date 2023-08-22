package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.compiler.SourceFile;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Module
public interface Globals {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

//    default void globalSource() {
//
//        Path globalSourcePath;
//        if(backend().context().getConfiguration().get(PlatformSettings.runOnNode)){
//            globalSourcePath = PathUtils.getTargetCodeGenSourceCC(backend().context()).resolve("globals.cc");
//        }else{
//            globalSourcePath = PathUtils.getTargetCodeGenSource(backend().context()).resolve("globals.cc");
//        }
//
//        emitter().open(globalSourcePath);
//
//        backend().includeUser("globals.h");
//        emitter().emitNewLine();
//
//        emitter().emit("// -- Type helper function Definitions");
//        emitter().emit("");
//        backend().algebraic().defineAlgebraic();
//        emitter().emit("");
//        backend().tuples().defineTuple();
//
//        emitter().emit("// -- Global variables" );
//        globalVariableDefinition(getGlobalVarDecls());
//        emitter().emitNewLine();
//
//        emitter().emit("// -- External Callables Definition");
//        backend().task().walk().forEach(backend().callablesInActor()::externalCallableDefinition);
//        emitter().emitNewLine();
//
//        emitter().emit("// -- Glabal Variable Initialization");
//        globalVariableInitializer(getGlobalVarDecls());
//        globalCallables(getGlobalVarDecls());
//        emitter().close();
//
//
//    }
//
//    default void globalHeader() {
//
//        Path globalHeaderPath;
//        if(backend().context().getConfiguration().get(PlatformSettings.runOnNode)){
//            globalHeaderPath = PathUtils.getTargetCodeGenIncludeCC(backend().context()).resolve("globals.h");
//        }else{
//            globalHeaderPath = PathUtils.getTargetCodeGenInclude(backend().context()).resolve("globals.h");
//        }
//
//        emitter().open(globalHeaderPath);
//
//        emitter().emit("#ifndef __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
//        emitter().emit("#define __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
//        emitter().emitNewLine();
//
//        // -- Headers
//        backend().includeSystem("stdio.h");
//        backend().includeSystem("stdint.h");
//        backend().includeSystem("stdbool.h");
//        backend().includeSystem("stdlib.h");
//        backend().includeSystem("string.h");
//        backend().includeUser("actors-rts.h");
//        backend().includeUser("natives.h");
//        emitter().emitNewLine();
//
//        emitter().emit("// -- Type declarations");
//        emitter().emit("");
//        backend().algebraic().forwardAlgebraic();
//        //emitter().emit("");
//        //backend().sets().forwardSet();
//        //emitter().emit("");
//        //backend().maps().forwardMap();
//        emitter().emit("");
//        backend().tuples().forwardTuple();
//
//        //emitter().emit("");
//        //backend().sets().declareSet();
//        //emitter().emit("");
//        //backend().maps().declareMap();
//        emitter().emit("");
//        backend().alias().declareAliasTypes();
//        emitter().emit("");
//        backend().algebraic().declareAlgebraic();
//        emitter().emit("");
//        backend().tuples().declareTuple();
//
//        emitter().emit("// -- External Callables Declaration");
//        backend().task().walk().forEach(backend().callablesInActor()::externalCallableDeclaration);
//        emitter().emitNewLine();
//        emitter().emit(" // -- Global Variables Declaration");
//        emitter().emit("void init_global_variables(void);");
//        globalVariableDeclarations(getGlobalVarDecls());
//
//        emitter().emitNewLine();
//        emitter().emit("#endif // __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
//
//
//        emitter().close();
//    }
//
//    default void getArtTypes(){
//        // -- Defines
//        emitter().emit("#define TRUE 1");
//        emitter().emit("#define FALSE 0");
//        emitter().emit("/*");
//        emitter().emit("#define TYPE_DIRECT");
//        emitter().emitNewLine();
//
//        // -- For now only max 4 dim and no optimization of struct size, {1,2,3,4,256};
//        // list all dimensions needed, if someone wants more than 4 dimensions they get 256 since they anyway don't care about memory usage
//        int dimensions[] = {4};
//        /*
//         * Print a type used to specify index or size
//         * of an array. Used by the array copy methods.
//         */
//        emitter().emit("typedef struct {");
//        emitter().increaseIndentation();
//        emitter().emit("int32_t len;");
//        emitter().emit("int32_t sz[%d];", dimensions[dimensions.length - 1]);
//        emitter().decreaseIndentation();
//        emitter().emit("} __arrayArg;");
//        emitter().emitNewLine();
//
//        /*
//         * Print a function used to calc a max of
//         * each dimension.
//         * targetSz: the assigned target's size
//         * exprSz: the assigning expression's size
//         * shift: how many dimensions (due to index) that is used from the targetSz.
//         */
//
//        emitter().emit("static inline __arrayArg maxArraySz(__arrayArg *targetSz, __arrayArg *exprSz, int shift) {");
//        emitter().increaseIndentation();
//        emitter().emit("__arrayArg ret;");
//        //Full array replace
//        emitter().emit("if (shift == 0) {");
//        emitter().increaseIndentation();
//        emitter().emit("return *exprSz;");
//        emitter().decreaseIndentation();
//        emitter().emit("}");
//        //Partial array replace shift = nbr of indices
//        emitter().emit("ret.len = targetSz->len;");
//        emitter().emit("memcpy(ret.sz, targetSz->sz, sizeof(int32_t) * 4);");
//        emitter().emit("for (int i = 0; i < exprSz->len; i++) {");
//        emitter().increaseIndentation();
//        emitter().emit("if (targetSz->sz[i + shift] < exprSz->sz[i])");
//        emitter().increaseIndentation();
//        emitter().emit("ret.sz[i + shift] = exprSz->sz[i];");
//        emitter().decreaseIndentation();
//        emitter().decreaseIndentation();
//        emitter().emit("}");
//        emitter().emit("return ret;");
//        emitter().decreaseIndentation();
//        emitter().emit("}");
//        emitter().emitNewLine();
//
//        /*
//         * These are the array types
//         * typedef struct {
//         *     union {
//         *         T_t* p;           pointer to array of builtin typed elements of the flattened array
//         *         T_t* (*pp);       pointer to array of pointers to user type elements of the flattened array
//         *     };
//         *     union { struct {
//         *         uint16_t flags;  flags see below
//         *         uint16_t dim;    dimensions valid in size array below
//         *     }; uint32_t flags_dim;};
//         *     int32_t sz[4];       size of each dimension (currently maximum of 4 allowed)
//         * } __array4T_t;           This metadata type is created for all the builtin types and all the user types
//         *
//         * Flag field:
//         *   Flags (true/false):
//         *   0:0x01 direct(p)/indirect(pp)  (FIXME not set correctly in all the code, remove it since anyway linked to user type?)
//         *   1:0x02 currently allocated(sz & pointer correct)/not-allocated (Very important to be correct)
//         *   2:0x04 on heap/on stack, (the data is currently mostly allocated on the heap and if the pointer is changed the old needs to be (deep) freed (we know non-other keeps pointers to lower levels due to copy semantics)
//         *   3:0x08 codegen (temporary created variable due to transformations go ahead and steel the memory)/cal variable (must obey copy semantics)
//         *   4:0x10 part of multi-dim (can't change the pointer or free etc, since pointing into the memory of a larger array)/the full array
//         */
//
//
//        //Print array metadata struct and array methods for all builtin types
//        for (String t : Arrays.asList("char", "int8_t", "uint8_t", "int16_t", "uint16_t", "int32_t", "uint32_t", "float", "double", "void", "_Bool")) {
//            for (int i : dimensions) {
//                emitter().emit("typedef struct {");
//                emitter().increaseIndentation();
//                emitter().emit("union {");
//                emitter().increaseIndentation();
//                emitter().emit("%s *p;", t);
//                emitter().emit("%s *(*pp);", t);
//                emitter().decreaseIndentation();
//                emitter().emit("};");
//                emitter().emit("union {");
//                emitter().increaseIndentation();
//                emitter().emit("struct {");
//                emitter().increaseIndentation();
//                emitter().emit("uint16_t flags;");
//                emitter().emit("uint16_t dim;");
//                emitter().decreaseIndentation();
//                emitter().emit("};");
//                emitter().emit("uint32_t flags_dim;");
//                emitter().decreaseIndentation();
//                emitter().emit("};");
//                emitter().emit("int32_t sz[%d];", i);
//                emitter().decreaseIndentation();
//                emitter().emit("} __array%d%s;", i, t);
//                emitter().emitNewLine();
//            }
//            emitter().emit("#define TYPE " + t);
//            emitter().emitNewLine();
//            emitter().emit("#include \"__arrayCopy.h\"");
//            emitter().emitNewLine();
//        }
//
//
//        emitter().emit("#undef TYPE_DIRECT");
//        emitter().emitNewLine();
//
//        emitter().emit("*/");
//    }
//
//
//    default Stream<VarDecl> getGlobalVarDecls() {
//        return backend().task()
//                .getSourceUnits().stream()
//                .flatMap(unit -> unit.getTree().getVarDecls().stream());
//    }
//
//    default void globalVariableDeclarations(Stream<VarDecl> varDecls) {
//        varDecls.forEach(decl -> {
//            Type type = backend().types().declaredType(decl);
//            if (type instanceof CallableType) {
//                if (decl.getValue() != null) {
//                    Expression expr = decl.getValue();
//                    if (expr instanceof ExprLambda || expr instanceof ExprProc) {
//                        emitter().emit("#ifndef TRACE_TURNUS");
//                        backend().callablesInActor().callablePrototypes(backend().variables().declarationName(decl), expr);
//                        emitter().emit("#else");
//                        backend().profilingbox().set(true);
//                        backend().callablesInActor().callablePrototypes(backend().variables().declarationName(decl), expr);
//                        backend().profilingbox().clear();
//                        emitter().emit("#endif");
//                        emitter().emitNewLine();
//                    }
//                }
//            } else {
//                String d = backend().declarations().declaration(type, backend().variables().declarationName(decl));
//                emitter().emit("extern %s;", d);
//            }
//        });
//    }
//
//
//    default void globalCallables(Stream<VarDecl> varDecls){
//        varDecls.forEach(decl -> {
//            Type type = backend().types().declaredType(decl);
//            if (type instanceof CallableType) {
//                if (decl.getValue() != null) {
//                    Expression expr = decl.getValue();
//                    if (expr instanceof ExprLambda || expr instanceof ExprProc) {
//                        emitter().emit("#ifndef TRACE_TURNUS");
//                        backend().callablesInActor().callableDefinition(backend().variables().declarationName(decl), expr);
//                        emitter().emit("#else");
//                        backend().profilingbox().set(true);
//                        backend().callablesInActor().callableDefinition(backend().variables().declarationName(decl), expr);
//                        backend().profilingbox().clear();
//                        emitter().emit("#endif");
//                        emitter().emitNewLine();
//                    }
//                }
//            }
//        });
//    }
//
//    default void globalVariableInitializer(Stream<VarDecl> varDecls) {
//        emitter().emit("void init_global_variables() {");
//        emitter().increaseIndentation();
//        varDecls.forEach(decl -> {
//            Type type = backend().types().declaredType(decl);
//            if (!(type instanceof CallableType)) {
//                emitter().emit("{");
//                emitter().increaseIndentation();
///*
//                if(type instanceof ListType){
//                    String maxIndex = backend().typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
//                    emitter().emit("%s = (%s*) calloc(%s, sizeof(%2$s));", backend().variables().declarationName(decl), backend().typeseval().type(type), maxIndex);
//                }
//*/
//                backend().statements().copy(type, backend().variables().declarationName(decl), backend().types().type(decl.getValue()), backend().expressionEval().evaluate(decl.getValue()));
//
//                emitter().decreaseIndentation();
//                emitter().emit("}");
//            }
//        });
//        emitter().decreaseIndentation();
//        emitter().emit("}");
//    }
//
//    default void globalVariableDefinition(Stream<VarDecl> varDecls) {
//        varDecls.forEach(decl -> {
//            Type type = backend().types().declaredType(decl);
//            if (!(type instanceof CallableType)) {
//                String d = backend().declarations().declaration(type, backend().variables().declarationName(decl));
//                emitter().emit("%s;", d);
//            }
//        });
//    }


    default void globalSource() {

        Path globalSourcePath;
        if (backend().context().getConfiguration().get(PlatformSettings.runOnNode)) {
            globalSourcePath = PathUtils.getTargetCodeGenSourceCC(backend().context()).resolve("globals.cc");
        } else {
            globalSourcePath = PathUtils.getTargetCodeGenSource(backend().context()).resolve("globals.cc");
        }

        emitter().open(globalSourcePath);

        emitter().emit("#include \"globals.h\"");

        emitter().emit("#ifdef USE_TORCH");
        emitter().emitNewLine();

        emitter().emit("char *serializeTensor(torch::Tensor *tensor, char *buffer) {");
        emitter().increaseIndentation();

        emitter().emit("char *p = buffer;");
        emitter().emit("std::stringstream ss;");
        emitter().emit("torch::save(*tensor, ss);");
        emitter().emit("long size = ss.str().size();");
        emitter().emit("std::memcpy(p, ss.str().c_str(), size);");
        emitter().emitNewLine();
        emitter().emit("return p + size;");

        emitter().decreaseIndentation();
        emitter().emit("}");

        emitter().emitNewLine();

        emitter().emit("char *deserializeTensor(torch::Tensor **tensor, char *buffer, long size) {");
        emitter().increaseIndentation();

        emitter().emit("char *p = buffer;");
        emitter().emit("std::stringstream ss;");
        emitter().emitNewLine();


        emitter().emit("for (long i = 0; i < size; i++) {");
        emitter().emit("\tss << buffer[i];");
        emitter().emit("}");

        emitter().emitNewLine();
        emitter().emit("Tensor load;");
        emitter().emit("torch::load(load, ss);");
        emitter().emit("Tensor* p_load = new Tensor(load);");
        emitter().emit("*tensor = new torch::Tensor[1];");
        emitter().emit("tensor[0] = p_load;");
        emitter().emitNewLine();

        emitter().emit("return p + size;");

        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();

        emitter().emit("long sizeTensor(torch::Tensor *src) {");
        emitter().increaseIndentation();

        emitter().emit("std::stringstream ss;");
        emitter().emit("torch::save(*src, ss);");
        emitter().emit("return ss.str().size();");

        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();

        emitter().emit("int freeTensor(torch::Tensor *tensor, int top) {");
        emitter().increaseIndentation();

        emitter().emit("if (tensor == nullptr) {");
        emitter().emit("\treturn 0;");
        emitter().emit("}");

        emitter().emitNewLine();
        emitter().emit("delete tensor;");
        emitter().emit("return 1;");

        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();

        emitter().emit("torch::Tensor torch::load_tensor_from_file(std::string fileName) {");
        {
            emitter().increaseIndentation();

            emitter().emit("std::ifstream file(fileName, std::ios::binary);");
            emitter().emit("std::vector<char> data((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());");
            emitter().emit("torch::IValue ivalue = torch::pickle_load(data);");
            emitter().emit("torch::Tensor tensor = ivalue.toTensor();");
            emitter().emit("return tensor;");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
        emitter().emit("#endif");

        Map<QID, List<SourceUnit>> sourceunitbyQID = getSourceUnitbyQid();
        emitter().emit("// -- Global variables");

        for (QID qid : sourceunitbyQID.keySet()) {
            globalVariableDefinition(sourceunitbyQID.get(qid).stream().flatMap(unit -> unit.getTree().getVarDecls().stream()), qid);
        }
        emitter().emitNewLine();

        emitter().emit("void init_global_variables(){");
        {
            emitter().increaseIndentation();

            for (QID qid : sourceunitbyQID.keySet()) {
                globalVariableInitialization(sourceunitbyQID.get(qid).stream().flatMap(unit -> unit.getTree().getVarDecls().stream()), qid);
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();


        emitter().close();
    }

    default void generateHeader() {
        Path mainTarget;
        if (backend().context().getConfiguration().get(PlatformSettings.runOnNode)) {
            mainTarget = PathUtils.getTargetCodeGenIncludeCC(backend().context()).resolve("globals.h");
        } else {
            mainTarget = PathUtils.getTargetCodeGenInclude(backend().context()).resolve("globals.h");
        }

        emitter().open(mainTarget);
        emitter().emit("#ifndef __GLOBAL__");
        emitter().emit("#define __GLOBAL__");
        emitter().emitNewLine();
        emitter().emit("#include <cstdint>");
        emitter().emit("#include <cstring>");
        emitter().emit("#include <set>");
        emitter().emit("#include <algorithm>");
        emitter().emitNewLine();

        backend().includeUser("art_time.h");
        emitter().emitNewLine();

        emitter().emit("#ifdef USE_TORCH");
        backend().includeSystem("torch/torch.h");
        backend().includeSystem("ATen/TensorIndexing.h");
        emitter().emitNewLine();


        emitter().emit("using namespace torch::indexing;");
        emitter().emit("typedef torch::Tensor Tensor;");
        emitter().emit("#define sb_null None");
        emitter().emitNewLine();

        emitter().emit("// -- Ser/Des Tensor functions");
        emitter().emit("char *serializeTensor(torch::Tensor *tensor, char *buffer);");
        emitter().emit("char *deserializeTensor(torch::Tensor **tensor, char *buffer, long size);");
        emitter().emit("long sizeTensor(torch::Tensor *src);");
        emitter().emit("int freeTensor(torch::Tensor *tensor, int top);");

        emitter().emit("namespace torch {");
        emitter().emit("\ttorch::Tensor load_tensor_from_file(std::string fileName);");
        emitter().emit("}");

        emitter().emitNewLine();

        emitter().emit("#endif");
        emitter().emitNewLine();

        // -- Prelude
        emitter().emit("// -- Prelude");
        emitter().emit("namespace prelude {");
        {
            emitter().increaseIndentation();

            emitter().emit("inline void print(std::string s) {");
            emitter().emit("\tstd::cout << s;");
            emitter().emit("}");
            emitter().emitNewLine();

            emitter().emit("inline void println(std::string s) {");
            emitter().emit("\tstd::cout << s << std::endl;");
            emitter().emit("}");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        Map<QID, List<SourceUnit>> sourceunitbyQID = getSourceUnitbyQid();

        namespaceDeclaration(sourceunitbyQID);
        emitter().emitNewLine();

        emitter().emit("// -- Init global variables");
        emitter().emit("void init_global_variables();");
        emitter().emitNewLine();

        emitter().emit("#endif // __GLOBALS__");
        emitter().emitNewLine();

        emitter().close();
    }


    default void namespaceDeclaration(Map<QID, List<SourceUnit>> sourceUnitByQID) {
        for (QID qid : sourceUnitByQID.keySet()) {
            // -- Starting namespace
            for (String n : qid.parts()) {
                emitter().emit("namespace %s {", n);
                emitter().increaseIndentation();
            }

            // -- Global var declaration
            globalVariableExternalDefinition(sourceUnitByQID.get(qid).stream().flatMap(unit -> unit.getTree().getVarDecls().stream()));

            //-- Ending namespace
            for (int i = 0; i < qid.getNameCount(); i++) {
                emitter().decreaseIndentation();
                emitter().emit("}");
            }

        }
    }

    default Stream<VarDecl> getGlobalVarDecls() {
        return backend().task()
                .getSourceUnits().stream()
                .flatMap(unit -> unit.getTree().getVarDecls().stream());
    }


    default Map<QID, List<SourceUnit>> getSourceUnitbyQid() {
        Map<QID, List<SourceUnit>> qidUnits = new HashMap<>();
        backend().task().getSourceUnits().forEach(s -> {
            QID qid = s.getTree().getQID();
            if (s instanceof SourceFile)
                if (qidUnits.containsKey(qid)) {
                    qidUnits.get(qid).add(s);
                } else {
                    List<SourceUnit> su = new ArrayList<>();
                    su.add(s);
                    qidUnits.put(qid, su);
                }
        });

        return qidUnits;
    }

    default void globalVariableExternalDefinition(Stream<VarDecl> varDecls) {
        varDecls.forEach(decl -> {
            if (!decl.isExternal()) {
                Type type = backend().types().declaredType(decl);
                if (type instanceof CallableType) {
                    backend().callablesInActor().callableDefinition("", decl.getValue());
                    //emitter().emitNewLine();
                } else {
                    String d = backend().declarations().declaration(type, backend().variables().declarationName(decl));
                    emitter().emit("extern %s;", d);
                    //emitter().emitNewLine();
                }
            }
        });
    }

    default void globalVariableDefinition(Stream<VarDecl> varDecls, QID qid) {
        varDecls.forEach(decl -> {
            if (!decl.isExternal()) {
                Type type = backend().types().declaredType(decl);
                if (type instanceof CallableType) {
                    // -- Do nothing
                } else {
                    if (decl.isConstant()) {
                        String parts = qid.parts().stream()
                                .collect(Collectors.joining("::", "", ""));
                        String d = backend().variables().declarationName(decl);
                        String dType = backend().typeseval().type(type);

                        emitter().emit("%s %s%s;",dType,  parts.isEmpty() ? "" : parts + "::", d);
                    }
                }
            }
        });
    }

    default void globalVariableInitialization(Stream<VarDecl> varDecls, QID qid) {
        varDecls.forEach(decl -> {
            if (!decl.isExternal()) {
                Type type = backend().types().declaredType(decl);
                if (type instanceof CallableType) {
                    // -- Do nothing
                } else {
                    String d = backend().variables().declarationName(decl);
                    String v = "";
                    if (decl.getValue() instanceof ExprComprehension) {
                        Interpreter interpreter = backend().interpreter();
                        Environment environment = new Environment();
                        Value value = interpreter.eval((ExprComprehension) decl.getValue(), environment);
                        Expression expression = backend().converter().apply(value);
                        v = backend().expressionEval().evaluateOnlyValue((ExprList) expression);
                    } else if (type instanceof ListType) {
                        v = backend().expressionEval().evaluateOnlyValue((ExprList) decl.getValue());
                    } else {
                        v = backend().expressionEval().evaluate(decl.getValue());
                    }

                    if (decl.isConstant()) {
                        String parts = qid.parts().stream()
                                .collect(Collectors.joining("::", "", ""));
                        emitter().emit("%s%s = %s;", parts.isEmpty() ? "" : parts + "::", d, v);
                    }
                    //emitter().emitNewLine();
                }
            }
        });
    }


    default void globalVariableDeclarations(Stream<VarDecl> varDecls) {
        varDecls.forEach(decl -> {
            if (!decl.isExternal()) {
                Type type = backend().types().declaredType(decl);
                if (type instanceof CallableType) {
                    backend().callablesInActor().callableDefinition("", decl.getValue());
                    //emitter().emitNewLine();
                } else {
                    String d = backend().declarations().declaration(type, backend().variables().declarationName(decl));
                    String v = "";
                    if (decl.getValue() instanceof ExprComprehension) {
                        Interpreter interpreter = backend().interpreter();
                        Environment environment = new Environment();
                        Value value = interpreter.eval((ExprComprehension) decl.getValue(), environment);
                        Expression expression = backend().converter().apply(value);
                        v = backend().expressionEval().evaluateOnlyValue((ExprList) expression);
                    } else if (type instanceof ListType) {
                        v = backend().expressionEval().evaluateOnlyValue((ExprList) decl.getValue());
                    } else {
                        v = backend().expressionEval().evaluate(decl.getValue());
                    }

                    if (decl.isConstant()) {
                        emitter().emit("const %s = %s;", d, v);
                    } else {
                        emitter().emit("%s;", d);
                    }
                    //emitter().emitNewLine();
                }
            }
        });
    }


}
