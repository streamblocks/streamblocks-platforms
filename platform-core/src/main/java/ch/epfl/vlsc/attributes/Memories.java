package ch.epfl.vlsc.attributes;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.ModuleKey;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.attribute.VariableScopes;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.*;

import java.util.List;
import java.util.Optional;

@Module
public interface Memories {

    ModuleKey<Memories>  key = task ->
            MultiJ.from(Implementation.class)
                    .bind("types").to(task.getModule(Types.key))
                    .bind("variableScopes").to(task.getModule(VariableScopes.key))
                    .instance();


    /**
     * Computes the minimum size of a given type in bytes assuming that
     * a value of that type resides in a byte aligned memory
     * @param type - the type to computes its size in bits
     * @return - minimum number of bits required to implement the type
     */

    Optional<Long> sizeInBytes(Type type);

    /**
     * Returns true if the type is power of two aligned. If a type is not
     * power of two aligned then it can not have an external memory interface
     * @param type
     * @return
     */
    Boolean isPowerOfTwo(Type type);


    /**
     * Computes depth of a list
     * @param listType
     * @return
     */
    Long listDepth(ListType listType);

    /**
     * Finds the raw (inner most) type of the list
     * @param listType
     * @return
     */
    Type rawListType(ListType listType);


    @Module
    interface Implementation extends Memories {

        @Binding(BindingKind.INJECTED)
        Types types();

        @Binding(BindingKind.INJECTED)
        VariableScopes variableScopes();

        @Binding(BindingKind.LAZY)
        default ImmutableList.Builder<VarDecl> builder() {
            return ImmutableList.builder();
        }

        // The fallback method, the type size computation is
        // either not supported or is not implemented yet
        default  Optional<Long> sizeInBytes(Type type) {
            return Optional.empty();
        }

        // IntType can have arbitrary precision given by the
        // size parameter (e.g. int (size = 3) is a 3 bit int)
        // However, for practical purposes the size is rounded to
        // a byte aligned size in most implementation cases.
        default Optional<Long> sizeInBytes(IntType type) {

            if (type.getSize().isPresent()) {
                Long preciseSize = Long.valueOf(type.getSize().getAsInt());
                Long byteSize = Long.valueOf(8);
                Long alignedSize = (preciseSize - 1) / byteSize + 1;
                return Optional.of(alignedSize);
            } else {
                return Optional.of(Long.valueOf(4));
            }
        }

        default Optional<Long> sizeInBytes(AliasType type) {
            return sizeInBytes(type.getConcreteType());
        }

        // BoolType is usually implemented as an 8bit integer
        default Optional<Long> sizeInBytes(BoolType type) {
            return Optional.of(Long.valueOf(1));
        }


        default Optional<Long> sizeInBytes(CharType type) {
            return Optional.of(Long.valueOf(1));
        }

        default Optional<Long> sizeInBytes(ListType type) {

            Optional<Long> innerBytes = sizeInBytes(type.getElementType());
            if (innerBytes.isPresent() && type.getSize().isPresent()) {
                return Optional.of(innerBytes.get() * type.getSize().getAsInt());
            } else {
                return Optional.empty();
            }
        }


        default Type rawListType(ListType listType) {

            Type elementType = listType.getElementType();
            if (elementType instanceof ListType) {
                return rawListType((ListType) elementType);
            } else {
                return elementType;
            }
        }


        // Fallback method, meaning that either the type
        // either the method for the given type is not implemented
        // or that the type is not byte-aligned
        default Boolean isPowerOfTwo(Type type) {
            return false;
        }

        default Boolean isPowerOfTwo(IntType type) {
            if (type.getSize().isPresent()) {
                Integer preciseSize = type.getSize().getAsInt();
                return preciseSize > 0 && ( (preciseSize & (preciseSize - 1)) == 0);
            } else {
                return true;
            }
        }

        default Boolean isPowerOfTwo(CharType type) {
            return true;
        }

        default Boolean isPowerOfTwo(ListType type) {

            return isPowerOfTwo(type.getElementType());

        }


        default Long listDepth(ListType listType) {

            Type elementType = listType.getElementType();
            if (elementType instanceof ListType) {
                return Long.valueOf(listType.getSize().getAsInt()) * listDepth((ListType) elementType);
            } else {
                return Long.valueOf(listType.getSize().getAsInt());
            }
        }




    }
}
