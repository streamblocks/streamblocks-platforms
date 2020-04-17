package ch.epfl.vlsc.sw.ir;



import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;


import java.util.List;
import java.util.Optional;

public class PartitionHandle {

    private final String name;
    private final ImmutableList<Method> methods;
    private final ImmutableList<Field>  fields;
    private final Method constructor;
    private final Method destructor;


    public PartitionHandle(String name, List<Method> methods, List<Field> fields, Method constructor,
                           Method destructor) {
        this.name = name;
        this.methods = ImmutableList.from(methods);
        this.fields = ImmutableList.from(fields);
        this.constructor = constructor;
        this.destructor = destructor;
    }

    public static class Method {
        public final String name;
        public final Type retType;
        public final ImmutableList<Pair<Type, String>> args;
        public final boolean global;
        public Method(Type retType, String name, List<Pair<Type, String>> args, boolean global) {
            this.retType = retType;
            this.name = name;
            this.args = ImmutableList.from(args);
            this.global = global;
        }

        public static Pair<Type, String> MethodArg(String type, String name) {
            return Pair.of(Type.of(type), name);
        }
        public static Pair<Type, String> MethodArg(Type type, String name) {
            return Pair.of(type, name);
        }

        public static Method of(String retType, String name, List<Pair<Type, String>> args, boolean global) {
            return new Method(Type.of(retType), name, args, global);
        }

        public static Method of(Type retType, String name, List<Pair<Type, String>> args, boolean global) {
            return new Method(retType, name, args, global);
        }
        public static Method of(String retType, String name) {
            return new Method(Type.of(retType), name, ImmutableList.empty(), false);
        }
        public static Method of(Type retType, String name) {
            return new Method(retType, name, ImmutableList.empty(), false);
        }
        public static Method of(String retType, String name, List<Pair<Type, String>> args) {
            return Method.of(retType, name, args, false);
        }

        public static Method of(Type retType, String name, List<Pair<Type, String>> args) {
            return Method.of(retType, name, args, false);
        }
    }
    public static class Field {
        public final Pair<Type, String> field;
        public final String description;

        public Field (String type, String name, String description) {
            this.field = Pair.of(Type.of(type), name);
            this.description = description;
        }
        public Field (TypeExpr type, String name, String description) {
            this.field = Pair.of(Type.of(type), name);
            this.description = description;
        }
        public Field(Type type, String name, String description) {
            this.field = Pair.of(type, name);
            this.description = description;
        }

        /**
         * Factory methods
         */
        static public Field of(Type type, String name, String description) {
            return new Field(type, name, description);
        }

        static public Field of(String type, String name, String description) {
            return new Field(type, name, description);
        }

        static public Field of(TypeExpr type, String name, String description) {
            return new Field(type, name, description);
        }

        static public Field of(String type, String name) {
            return Field.of(type, name, "");
        }

        static public Field of(TypeExpr type, String name) {
            return Field.of(type, name);
        }

        static public Field of(Type type, String name) {
            return Field.of(type, name);
        }

    }
    public static class Pair<X, Y> {
        public final X _1;
        public final Y _2;
        public Pair(X x, Y y) {
            this._1 = x;
            this._2 = y;
        }
        public static <X, Y> Pair<X, Y> of(X x, Y y) {
            return new Pair(x, y);
        }
    }

    public static class Type {

        private final Optional<String> type;
        private final Optional<TypeExpr> typeExpr;
        private boolean isRef;

        public Type(String type, boolean isRef) {
            this.type = Optional.of(type);
            this.typeExpr = Optional.empty();
            this.isRef = isRef;
        }
        public Type(String type) {
            this(type, false);
        }
        public Type(TypeExpr type, boolean isRef) {
            this.type = Optional.empty();
            this.typeExpr = Optional.of(type);
            this.isRef = isRef;
        }
        public Type(TypeExpr type) {
            this(type, false);
        }


        public Boolean isEvaluated() { return type.isPresent(); }
        public Optional<TypeExpr> getTypeExpr() {return this.typeExpr; }
        public Optional<String> getType() { return this.type; }

        public static Type of(String type, boolean isRef) { return new Type(type, isRef); }
        public static Type of(TypeExpr type, boolean isRef) { return new Type(type, isRef); }
        public static Type of(String type) { return new Type(type); }
        public static Type of(TypeExpr typeExpr) { return new Type(typeExpr); }
    }



}
