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
    Types types();

    @Binding(INJECTED)
    Memories memories();

    default String name(Instance instance, VarDecl decl) {
        return memories().name(instance.getInstanceName(), decl);
    }

    default String name(String instanceName, VarDecl decl) {
        return memories().name(instanceName, decl);
    }

    default String namePair(Memories.InstanceVarDeclPair pair) {
        return memories().name(pair.getInstance(), pair.getDecl());
    }

    default ImmutableList<Memories.InstanceVarDeclPair> getExternalMemories(Network network) {
        return memories().getExternalMemories(network);
    }


    default ImmutableList<VarDecl> getExternalMemories(Entity entity) {
        return memories().getExternalMemories(entity);
    }



    default Boolean isStoredExternally(VarDecl decl) { return memories().isStoredExternally(decl); }
    default ImmutableList<Memories.Pair<String, Integer>> getAxiParamPairs(Memories.InstanceVarDeclPair pair) {

        String memName = namePair(pair).toUpperCase();

        Integer axiDataWidth = getAxiWidth(pair);

        return ImmutableList.of(
            Memories.Pair.of(String.format("C_M_AXI_%s_ADDR_WIDTH", memName), AxiConstants.C_M_AXI_ADDR_WIDTH),
            Memories.Pair.of(String.format("C_M_AXI_%s_DATA_WIDTH", memName), axiDataWidth),
            Memories.Pair.of(String.format("C_M_AXI_%s_ID_WIDTH", memName), 1),
            Memories.Pair.of(String.format("C_M_AXI_%s_AWUSER_WIDTH", memName), 1),
            Memories.Pair.of(String.format("C_M_AXI_%s_ARUSER_WIDTH", memName), 1),
            Memories.Pair.of(String.format("C_M_AXI_%s_WUSER_WIDTH", memName), 1),
            Memories.Pair.of(String.format("C_M_AXI_%s_RUSER_WIDTH", memName), 1),
            Memories.Pair.of(String.format("C_M_AXI_%s_BUSER_WIDTH", memName), 1)
        );

    }


    default ImmutableList<String> getAxiParams(Memories.InstanceVarDeclPair pair) {
        return getAxiParamPairs(pair).map(
                p -> String.format("parameter integer %s = %d", p.getFirst(), p.getSecond()));

    }

    default Integer getAxiWidth(Memories.InstanceVarDeclPair pair) {
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
}
