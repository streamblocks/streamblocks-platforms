package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.hls.backend.kernel.AxiConstants;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.settings.SizeValueParser;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.attribute.VariableScopes;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.*;

import static org.multij.BindingKind.INJECTED;

@Module
public interface ExternalMemory {

    @Binding(INJECTED)
    VariableScopes variableScopes();

    @Binding(INJECTED)
    Types types();

    @Binding(INJECTED)
    TypesEvaluator typeseval();

    @Binding(INJECTED)
    Memories memories();



    @Binding(INJECTED)
    CompilationTask task();


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


    /**
     * Collects all of the external memories in a given network, sorted lexicographically depending
     * on the name produced by the name() method.
     * @param network
     * @return A sorted list of Instance VarDecl pairs
     */
    default ImmutableList<InstanceVarDeclPair> getExternalMemories(Network network) {

        List<InstanceVarDeclPair> list = new ArrayList<>();

        for (Instance instance: network.getInstances()) {
            Entity entity = task().getModule(GlobalNames.key).entityDecl(instance.getEntityName(), true).getEntity();

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
        if (annotation.getName().equals("HLS") && annotation.getParameters().get(0).equals("external")) {
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


    default ImmutableList<Pair<String, Integer>> getAxiParamPairs(InstanceVarDeclPair pair) {

        String memName = namePair(pair);

        Integer axiDataWidth = getAxiWidth(pair);

        return ImmutableList.of(
            Pair.of(String.format("C_M_AXI_%s_ADDR_WIDTH", memName), AxiConstants.C_M_AXI_ADDR_WIDTH),
            Pair.of(String.format("C_M_AXI_%s_DATA_WIDTH", memName), axiDataWidth),
            Pair.of(String.format("C_M_AXI_%s_ID_WIDTH", memName), 1),
            Pair.of(String.format("C_M_AXI_%s_AWUSER_WIDTH", memName), 1),
            Pair.of(String.format("C_M_AXI_%s_ARUSER_WIDTH", memName), 1),
            Pair.of(String.format("C_M_AXI_%s_WUSER_WIDTH", memName), 1),
            Pair.of(String.format("C_M_AXI_%s_RUSER_WIDTH", memName), 1),
            Pair.of(String.format("C_M_AXI_%s_BUSER_WIDTH", memName), 1)
        );

    }


    default ImmutableList<String> getAxiParams(InstanceVarDeclPair pair) {
        return getAxiParamPairs(pair).map(
                p -> String.format("parameter integer %s = %d", p.getFirst(), p.getSecond()));

    }

    default Integer getAxiWidth(InstanceVarDeclPair pair) {
        VarDecl decl = pair.getDecl();
        ListType listType = (ListType) types().declaredType(decl);
        Type rawListType = memories().rawListType(listType);
        Long bitWidth = memories().sizeInBytes(rawListType).orElseThrow(
                () -> new CompilationException(
                        new Diagnostic(
                                Diagnostic.Kind.ERROR,
                                "Could not get the bitwidth for " + decl.getName()))) * Long.valueOf(8);
        String memName = namePair(pair);

        Integer axiDataWidth = AxiConstants.getAxiDataWidth(bitWidth.intValue()).orElseThrow(
                () -> new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, "Can not create axi port " + memName + " with " +
                                "width " + bitWidth)));
        return axiDataWidth;
    }

//
//    @Binding(BindingKind.LAZY)
//    default Map<VarDecl, String> externalMemories() {
//        return new HashMap<>();
//    }
//    Map<VarDecl, String> getExternalMemories(Entity entiy);
//
//    default int sizeThresholdBits() {
//        String text = backend().context().getConfiguration().get(PlatformSettings.maxBRAMSize);
//        Long maxBramSizeBytes = new SizeValueParser().parse(text);
//
//        return maxBramSizeBytes.intValue() * 8;
//
//    }
//
//    default int getListSizeBits(ListType listType) {
//        Type elementType = listType.getElementType();
//        List<Integer> dim = typeseval().sizeByDimension(listType);
//        int listSize = dim.stream().mapToInt(s -> s).reduce(1, Math::multiplyExact);
//        int bitSize = typeseval().bitPerType(elementType);
//        int size = listSize * bitSize;
//        return size;
//    }
//    default Map<VarDecl, String> getExternalMemories(ActorMachine actorMachine) {
//        Map<VarDecl, String> externalVars = new HashMap<>();
//        List<VarDecl> variables = variableScopes().declarations(actorMachine);
//        for (VarDecl decl : variables) {
//            Type type = types().declaredType(decl);
//            if (type instanceof ListType) {
//                ListType listType = (ListType) type;
//
//
//                if (getListSizeBits(listType) > sizeThresholdBits()) {
//                    if (!externalMemories().containsKey(decl)) {
//                        int mapSize = externalMemories().size() + 1;
//                        externalMemories().put(decl, "mem_" + mapSize);
//                        externalVars.put(decl, "mem_" + mapSize);
//                    } else {
//                        externalVars.put(decl, externalMemories().get(decl));
//                    }
//                }
//            }
//        }
//
//        return externalVars;
//    }
//
//
//    default Map<VarDecl, String> getExternalMemories(CalActor calActor) {
//        Map<VarDecl, String> externalVars = new HashMap<>();
//        List<VarDecl> variables = variableScopes().declarations(calActor);
//        for (VarDecl decl : variables) {
//            Type type = types().declaredType(decl);
//            if (type instanceof ListType) {
//                ListType listType = (ListType) type;
//
//                if (getListSizeBits(listType) > sizeThresholdBits()) {
//                    if (!externalMemories().containsKey(decl)) {
//                        int mapSize = externalMemories().size() + 1;
//                        externalMemories().put(decl, "mem_" + mapSize);
//                        externalVars.put(decl, "mem_" + mapSize);
//                    } else {
//                        externalVars.put(decl, externalMemories().get(decl));
//                    }
//                }
//            }
//        }
//
//        return externalVars;
//    }
//
//
//    default Map<VarDecl, String> getExternalVariables() {
//        return externalMemories();
//    }
//
//    default boolean isExternalMemory(VarDecl decl) {
//        return externalMemories().containsKey(decl);
//    }
}
