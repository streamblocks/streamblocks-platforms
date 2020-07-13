package ch.epfl.vlsc.attributes;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.ModuleKey;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.attribute.VariableScopes;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.*;

import java.util.*;


/**
 * This interface defines a set of utility functions used to annotate external memories
 */
@Module
public interface Memories {

    ModuleKey<Memories>  key = task ->
            MultiJ.from(Implementation.class)
                    .bind("globalNames").to(task.getModule(GlobalNames.key))
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



    class Pair<U, V> {
        private final U first;
        private final V second;
        public Pair(U first, V second) {
            this.first = first;
            this.second = second;
        }
        public static <U, V> Pair<U, V> of(U first, V second) {
            return new Pair<U, V>(first, second);
        }
        public U getFirst() {
            return first;
        }

        public V getSecond() {
            return second;
        }
    }
    class InstanceVarDeclPair extends Pair<Instance, VarDecl>{

        public InstanceVarDeclPair(VarDecl decl, Instance instance) {
            super(instance, decl);
        }

        public Instance getInstance() {
            return getFirst();
        }

        public VarDecl getDecl() {
            return getSecond();
        }
    }



    default String name(Instance instance, VarDecl decl) {
        return name(instance.getInstanceName(), decl);
    }

    default String name(String instanceName, VarDecl decl) {
        return instanceName + "_" + decl.getName();
    }

    default String namePair(InstanceVarDeclPair pair) {
        return name(pair.getInstance(), pair.getDecl());
    }

    /**
     * Collects all of the external memories in a given network, sorted lexicographically depending
     * on the name produced by the name() method.
     * @param network
     * @return A sorted list of Instance VarDecl pairs
     */
    ImmutableList<InstanceVarDeclPair> getExternalMemories(Network network);

    /**
     * Collects all the external memories in a given entity, not sorted.
     * @param entity
     * @return A list VarDecl that are supposed to be stored externally
     */
    ImmutableList<VarDecl> getExternalMemories(Entity entity);



    Boolean isStoredExternally(VarDecl decl);

    @Module
    interface Implementation extends Memories {

        @Binding(BindingKind.INJECTED)
        Types types();

        @Binding(BindingKind.INJECTED)
        VariableScopes variableScopes();


        @Binding(BindingKind.INJECTED)
        GlobalNames globalNames();

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
        // a byte aligned size in most implementation cases because memory addresses are byte-aligned.
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




        /**
         * Collects all of the external memories in a given network, sorted lexicographically depending
         * on the name produced by the name() method.
         * @param network
         * @return A sorted list of Instance VarDecl pairs
         */
        default ImmutableList<InstanceVarDeclPair> getExternalMemories(Network network) {

            List<InstanceVarDeclPair> list = new ArrayList<>();

            for (Instance instance: network.getInstances()) {
                Entity entity = globalNames().entityDecl(instance.getEntityName(), true).getEntity();

                list.addAll(getExternalMemories(entity).map(decl -> new InstanceVarDeclPair(decl, instance)));
            }
            Comparator<InstanceVarDeclPair> comparator = new Comparator<InstanceVarDeclPair>() {
                @Override
                public int compare(InstanceVarDeclPair first, InstanceVarDeclPair second) {
                    String firstName = name(first.getInstance(), first.getDecl());
                    String secondName = name(second.getInstance(), second.getDecl());
                    return firstName.compareTo(secondName);
                }
            };

            Collections.sort(list, comparator);

            return list.stream().collect(ImmutableList.collector());

        }

        default ImmutableList<VarDecl> getExternalMemories(Entity entity) {

            return variableScopes().declarations(entity)
                    .stream().filter(this::isStoredExternally).collect(ImmutableList.collector());
        }



        default Boolean isStoredExternally(VarDecl decl) {

            return decl.getAnnotations().stream().filter(this::isHLSStorageAnnotation).count() > 0;

        }

        default Boolean isHLSStorageAnnotation(Annotation annotation) {
            if (annotation.getName().equals("HLS") && annotation.getParameters().get(0).getName().equals("external")) {
                // TODO: Do better handling of HLS annotations, what if there are other
                // types of HLS annotations added?
                return true;

            } else {
                return false;
            }
        }


        default String name(Instance instance, VarDecl decl) {
            return name(instance.getInstanceName(), decl);
        }

        default String name(String instanceName, VarDecl decl) {
            return instanceName + "_" + decl.getName();
        }

        default String namePair(InstanceVarDeclPair pair) {
            return name(pair.getInstance(), pair.getDecl());
        }



    }
}
