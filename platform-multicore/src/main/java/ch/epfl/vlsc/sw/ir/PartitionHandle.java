package ch.epfl.vlsc.sw.ir;



import ch.epfl.vlsc.attributes.Memories;
import se.lth.cs.tycho.ir.entity.PortDecl;

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



    public ImmutableList<Method> getMethods() { return this.methods; }

    public ImmutableList<Field> getFields() { return this.fields; }

    public Method getConstructor() { return this.constructor; }

    public Method getDestructor() { return this.destructor; }

    public String getName() { return this.name; }

    public Optional<Method> findMethod(String methodName) {
        return methods.stream().filter(
                m -> m.getName().equals(methodName))
                .findAny();
    }

    public Optional<Field> findField(String fieldName) {
        return fields.stream().filter(
                f -> f.getName().equals(fieldName))
                .findAny();
    }

    public static class Method {
        private final String name;
        private final Type retType;
        private final ImmutableList<Pair<Type, String>> args;
        private final boolean global;
        private final boolean fixed;
        public Method(Type retType, String name, List<Pair<Type, String>> args, boolean fixed, boolean global) {
            this.retType = retType;
            this.name = name;
            this.args = ImmutableList.from(args);
            this.global = global;
            this.fixed = fixed;
        }

        public String getName() { return this.name; }
        public Type getReturnType() { return this.retType; }
        public ImmutableList<Pair<Type, String>> getArgs() { return this.args; }
        public boolean isGlobal() { return global; }


        public static Pair<Type, String> MethodArg(String type, String name) {
            return Pair.of(Type.of(type), name);
        }
        public static Pair<Type, String> MethodArg(Type type, String name) {
            return Pair.of(type, name);
        }

        public static Method global(String retType, String name, List<Pair<Type, String>> args, boolean fixed) {
            return new Method(Type.of(retType), name, args, fixed, true);
        }

        public static Method global(String retType, String name, List<Pair<Type, String>> args) {
            return Method.global(retType, name, args, true);
        }

        public static Method of(String retType, String name, List<Pair<Type, String>> args, boolean fixed) {
            return new Method(Type.of(retType), name, args, fixed, false);
        }

        public static Method of(Type retType, String name, List<Pair<Type, String>> args, boolean fixed) {
            return new Method(retType, name, args, fixed, false);
        }
        public static Method of(String retType, String name) {
            return new Method(Type.of(retType), name, ImmutableList.empty(), false, false);
        }
        public static Method of(Type retType, String name) {
            return new Method(retType, name, ImmutableList.empty(), false, false);
        }
        public static Method of(String retType, String name, List<Pair<Type, String>> args) {
            return Method.of(retType, name, args, false);
        }

        public static Method of(Type retType, String name, List<Pair<Type, String>> args) {
            return Method.of(retType, name, args, false);
        }
    }
    public static class Field {

        private final String name;
        private final Type type;
        private final String description;

        public Field (String type, String name, String description) {
            this.type = Type.of(type);
            this.name = name;
            this.description = description;
        }
        public Field (PortDecl port, String name, String description) {
            this.name = name;
            this.type = Type.of(port);
            this.description = description;
        }
        public Field(Type type, String name, String description) {
            this.name = name;
            this.type = type;
            this.description = description;
        }

        public String getName() { return this.name; }
        public Type getType() { return this.type; }
        public String getDescription() { return this.description; }

        /**
         * Factory methods
         */
        static public Field of(Type type, String name, String description) {
            return new Field(type, name, description);
        }

        static public Field of(String type, String name, String description) {
            return new Field(type, name, description);
        }

        static public Field of(PortDecl port, String name, String description) {
            return new Field(port, name, description);
        }

        static public Field of(String type, String name) {
            return Field.of(type, name, "");
        }

        static public Field of(PortDecl port, String name) {
            return Field.of(port, name);
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
        private final Optional<PortDecl> port;
        private final Optional<Memories.InstanceVarDeclPair> mem;
        private boolean isRef;

        public Type(String type, boolean isRef) {
            this.type = Optional.of(type);
            this.port = Optional.empty();
            this.mem  = Optional.empty();
            this.isRef = isRef;
        }
        public Type(String type) {
            this(type, false);
        }
        public Type(PortDecl port, boolean isRef) {
            this.type = Optional.empty();
            this.port = Optional.of(port);
            this.isRef = isRef;
            this.mem = Optional.empty();
        }
        public Type(PortDecl port) {
            this(port, false);
        }
        public Type(Memories.InstanceVarDeclPair mem) {
            this.type = Optional.empty();
            this.port = Optional.empty();
            this.mem = Optional.of(mem);
            this.isRef = false;
        }

        public boolean isEvaluated() { return type.isPresent(); }
        public Optional<PortDecl> getPort() {return this.port; }
        public Optional<String> getType() { return this.type; }
        public boolean isReference() { return this.isRef; }

        public static Type of(String type, boolean isRef) { return new Type(type, isRef); }
        public static Type of(PortDecl port, boolean isRef) { return new Type(port, isRef); }
        public static Type of(String type) { return new Type(type); }
        public static Type of(PortDecl port) { return new Type(port); }
        public static Type of(Memories.InstanceVarDeclPair mem) { return new Type(mem); }
    }



}
