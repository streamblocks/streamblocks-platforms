package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.settings.SizeValueParser;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.attribute.VariableScopes;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.multij.BindingKind.INJECTED;

@Module
public interface ExternalMemory {

    @Binding(INJECTED)
    VariableScopes variableScopes();

    @Binding(INJECTED)
    Types types();

    @Binding(INJECTED)
    TypesEvaluator typeseval();


    @Binding(BindingKind.LAZY)
    default Map<VarDecl, String> externalMemories() {
        return new HashMap<>();
    }

    @Binding(INJECTED)
    VivadoHLSBackend backend();

    Map<VarDecl, String> getExternalMemories(Entity entiy);

    default int sizeThresholdBits() {
        String text = backend().context().getConfiguration().get(PlatformSettings.maxBRAMSize);
        Long maxBramSizeBytes = new SizeValueParser().parse(text);

        return maxBramSizeBytes.intValue() * 8;

    }

    default int getListSizeBits(ListType listType) {
        Type elementType = listType.getElementType();
        List<Integer> dim = typeseval().sizeByDimension(listType);
        int listSize = dim.stream().mapToInt(s -> s).reduce(1, Math::multiplyExact);
        int bitSize = typeseval().bitPerType(elementType);
        int size = listSize * bitSize;
        return size;
    }
    default Map<VarDecl, String> getExternalMemories(ActorMachine actorMachine) {
        Map<VarDecl, String> externalVars = new HashMap<>();
        List<VarDecl> variables = variableScopes().declarations(actorMachine);
        for (VarDecl decl : variables) {
            Type type = types().declaredType(decl);
            if (type instanceof ListType) {
                ListType listType = (ListType) type;


                if (getListSizeBits(listType) > sizeThresholdBits()) {
                    if (!externalMemories().containsKey(decl)) {
                        int mapSize = externalMemories().size() + 1;
                        externalMemories().put(decl, "mem_" + mapSize);
                        externalVars.put(decl, "mem_" + mapSize);
                    } else {
                        externalVars.put(decl, externalMemories().get(decl));
                    }
                }
            }
        }

        return externalVars;
    }


    default Map<VarDecl, String> getExternalMemories(CalActor calActor) {
        Map<VarDecl, String> externalVars = new HashMap<>();
        List<VarDecl> variables = variableScopes().declarations(calActor);
        for (VarDecl decl : variables) {
            Type type = types().declaredType(decl);
            if (type instanceof ListType) {
                ListType listType = (ListType) type;

                if (getListSizeBits(listType) > sizeThresholdBits()) {
                    if (!externalMemories().containsKey(decl)) {
                        int mapSize = externalMemories().size() + 1;
                        externalMemories().put(decl, "mem_" + mapSize);
                        externalVars.put(decl, "mem_" + mapSize);
                    } else {
                        externalVars.put(decl, externalMemories().get(decl));
                    }
                }
            }
        }

        return externalVars;
    }


    default Map<VarDecl, String> getExternalVariables() {
        return externalMemories();
    }

    default boolean isExternalMemory(VarDecl decl) {
        return externalMemories().containsKey(decl);
    }
}
