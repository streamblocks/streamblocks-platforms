package ch.epfl.vlsc.wsim.phase.attributes;

import ch.epfl.vlsc.wsim.ir.cpp.types.ArrayCppType;
import ch.epfl.vlsc.wsim.ir.cpp.types.CppType;
import ch.epfl.vlsc.wsim.ir.cpp.types.NativeTypeCpp;
import ch.epfl.vlsc.wsim.ir.cpp.types.VectorCppType;
import org.multij.Module;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.*;

/**
 * A functional interface for converting high level CAL types to lower level
 * C++ types
 */

@Module
public interface CppTypeConversion {


    default <T extends Type> CppType unimplConv(Class<T> type) {
        throw new CompilationException(
                new Diagnostic(Diagnostic.Kind.ERROR,
                        "Unimplemented type conversion  ")
        );

    }

    CppType convert(Type t);

    default CppType convert(BottomType t) {
        return unimplConv(t.getClass());
    }

    default CppType convert(ListType t) {
        Type elementType = t.getElementType();
        if (t.getSize().isPresent()) {
            // if the array is small enough, allocate on the stack
//            if (t.getSize().getAsInt() <= 64)
//                return new ArrayCppType(convert(elementType), t.getSize().getAsInt());
//            else
                return new VectorCppType(convert(elementType), t.getSize().getAsInt());
        } else {
            return new VectorCppType(convert(elementType));
        }
    }

    default CppType convert(SetType t) {
        return unimplConv(t.getClass());
    }

    default CppType convert(MapType t) {
        return unimplConv(t.getClass());
    }

    default CppType convert(QueueType t) {
        return unimplConv(t.getClass());
    }

    default CppType convert(IntType t) {
        final int size = t.getSize().orElse(32);
        final boolean signed = t.isSigned();
        if (size == 1) {
            return NativeTypeCpp.Bool();
        } else {
            if (size <= 8) {
                return NativeTypeCpp.Int8(signed);
            } else if (size <= 16) {
                return NativeTypeCpp.Int16(signed);
            } else if (size <= 32) {
                return NativeTypeCpp.Int32(signed);
            } else if (size <= 64) {
                return NativeTypeCpp.Int64(signed);
            } else {
                throw new CompilationException(new Diagnostic(
                        Diagnostic.Kind.ERROR, "Can not convert " + t.toString() +
                        " to C++ native types."
                ));
            }
        }
    }


    default CppType convert(BoolType t) {
        return NativeTypeCpp.Bool();
    }

    default CppType convert(CharType t) {
        return NativeTypeCpp.Char();
    }

    default CppType convert(UnitType t) {
        return NativeTypeCpp.Void();
    }

    default CppType convert(RealType t) {

        final int size = t.getSize();
        if (size <= 32) {
            return NativeTypeCpp.Float();
        } else if (size <= 64) {
            return NativeTypeCpp.Double();
        } else {
            throw new CompilationException(
                    new Diagnostic(
                            Diagnostic.Kind.ERROR,
                            "Can not convert " + t.toString() + " to C++ native types."
                    )
            );
        }
    }

    // remove aliasing
    default CppType convert(AliasType t) {
        return convert(t.getType());
    }

    default CppType convert(SumType t) {
        return unimplConv(t.getClass());
    }

    default CppType convert(ProductType t) {
        return unimplConv(t.getClass());
    }
}