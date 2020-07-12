package ch.epfl.vlsc.attributes;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.ModuleKey;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.attribute.VariableScopes;
import se.lth.cs.tycho.compiler.Context;
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
     * Computes the minimum size of a given type in bits assuming that
     * a value of that type resides in a byte aligned memory
     * @param type - the type to computes its size in bits
     * @return - minimum number of bits required to implement the type
     */

    Optional<Long> sizeInBits(Type type);

    /**
     * Returns true if the type is byte-aligned. If a type is not
     * byte-aligned then it can have an external memory interface
     * @param type
     * @return
     */
    Boolean isByteAligned(Type type);

    @Module
    interface Implementation extends Memories {

        @Binding(BindingKind.INJECTED)
        Types types();

        @Binding(BindingKind.INJECTED)
        VariableScopes variableScopes();

        // The fallback method, the type size computation is
        // either not supported or is not implemented yet
        default  Optional<Long> sizeInBits(Type type) {
            return Optional.empty();
        }

        // IntType can have arbitrary precision given by the
        // size parameter (e.g. int (size = 3) is a 3 bit int)
        // However, for practical purposes the size is rounded to
        // a byte aligned size in most implementation cases.
        default Optional<Long> sizeInBits(IntType type) {

            if (type.getSize().isPresent()) {
                Long preciseSize = Long.valueOf(type.getSize().getAsInt());
                Long byteSize = Long.valueOf(8);
                Long alignedSize = (preciseSize - 1) / byteSize + 1;
                return Optional.of(alignedSize);
            } else {
                return Optional.of(Long.valueOf(32));
            }
        }

        default Optional<Long> sizeInBits(AliasType type) {
            return sizeInBits(type.getConcreteType());
        }


        // BoolType is usually implemented as an 8bit integer
        default Optional<Long> sizeInBits(BoolType type) {
            return Optional.of(Long.valueOf(8));
        }


        default Optional<Long> sizeInBits(CharType type) {
            return Optional.of(Long.valueOf(8));
        }

        default Optional<Long> sizeInBits(ListType type) {

            Optional<Long> innerBits = sizeInBits(type.getElementType());
            if (innerBits.isPresent() && type.getSize().isPresent()) {
                return Optional.of(innerBits.get() * type.getSize().getAsInt());
            } else {
                return Optional.empty();
            }
        }


        // Fallback method, meaning that either the type
        // either the method for the given type is not implemented
        // or that the type is not byte-aligned
        default Boolean isByteAligned(Type type) {
            return false;
        }

        default Boolean isByteAligned(IntType type) {
            if (type.getSize().isPresent()) {
                Integer preciseSize = type.getSize().getAsInt();
                Integer byteSize = 8;
                return (preciseSize % 8 == 0) ? true : false;
            } else {
                return true;
            }
        }

        default Boolean isByteAligned(CharType type) {
            return true;
        }



    }
}
