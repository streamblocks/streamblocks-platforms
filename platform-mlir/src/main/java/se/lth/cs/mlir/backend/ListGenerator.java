package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.OptionalInt;

/**
 * A module that generates MLIR operations for dealing with CAL lists represented as memrefs in MLIR.
 */
@Module
public interface ListGenerator {
    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void allocateList(ListType lvalueType, String lvalueSSA) {
        String typeString = backend().typeseval().type(lvalueType);
        backend().emitter().emit("%%%s = memref.alloca() : %s", lvalueSSA, typeString);
    }

    default void store(String listSSA, String ssaToStore, String indexSSA, Type listType) {
        emitter().emit("memref.store %%%s, %%%s[%%%s] : %s", ssaToStore, listSSA, indexSSA,
                backend().typeseval().type(listType));
    }

    default void load(String listSSA, String ssaDest, String indexSSA, Type listType) {
        emitter().emit("%%%s = memref.load %%%s[%%%s] : %s", ssaDest, listSSA, indexSSA,
                backend().typeseval().type(listType));
    }

    default String generateIndexFromInt(int index) {
        String tempSSA = backend().ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.constant %d: index", tempSSA, index);
        return tempSSA;
    }

    /**
     * Convert an expression to the MLIR index type.
     *
     * @param indexExprType The type of the expression containing the index that needs to be converted
     * @param ssaToConvert  The SSA operand that will be converted to the index
     * @return The SSA result representing the index type
     * @throws Error If no conversion has been implemented or is possible
     */
    default String generateIndex(Type indexExprType, String ssaToConvert) {
        throw new Error("castToIndex type conversion not implemented from type:" + indexExprType.getClass() + " to " +
                "index.");
    }

    default String generateIndex(IntType indexExprType, String ssaToConvert) {
        // 2024/05/27: The arith dialect in MLIR supports two different versions of casting an integer to an index.
        // index_cast that performs sign extension and index_castui that does not perform sign extension. Tycho tends
        // to store integers in the smallest size possible which means that we end up having lots of unsigned ints
        // where the MSB is one. This means that index_castui needs to be used. However, as of writing this comment
        // index_cast works while index_cast_ui throws not supported errors in CIRCT. As such we cast to an expected
        // larger integer size to get around the sign extension issue.
        IntType widerIndex;
        if (indexExprType.getSize().orElse(32) <= 32) {
            widerIndex = new IntType(OptionalInt.of(32), false);
        } else {
            widerIndex = new IntType(OptionalInt.of(64), false);
        }
        String tempSsa = backend().typeseval().castType(indexExprType, widerIndex, ssaToConvert);
        String outputSsa = backend().ssaValueNumberingStack().getNewTempVar();
        String type = backend().typeseval().type(widerIndex);
        emitter().emit("%%%s = arith.index_cast %%%s: %s to index", outputSsa, tempSsa, type);
        return outputSsa;
    }
}