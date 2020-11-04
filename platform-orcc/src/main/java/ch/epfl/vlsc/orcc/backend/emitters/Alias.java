package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.AliasTypeDecl;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.AliasType;
import se.lth.cs.tycho.type.Type;

import java.util.stream.Stream;

@Module
public interface Alias {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default void declareAliasTypes() {
        emitter().emit("// ALIAS DECLARATIONS");
        types().forEachOrdered(this::declareAlias);
    }

    default void declareAlias(AliasType alias) {
        emitter().emit("typedef %s %s;", typeseval().type(alias.getType()), alias.getName());
        emitter().emit("");
    }

    default Stream<AliasType> types() {
        return backend().task()
                .getSourceUnits().stream()
                .flatMap(unit -> unit.getTree().getTypeDecls().stream())
                .filter(AliasTypeDecl.class::isInstance)
                .map(decl -> (AliasType) backend().types().declaredGlobalType(decl));
    }

    default boolean isAlgebraicType(Type type) {
        return type instanceof AliasType && ((AliasType) type).getConcreteType() instanceof AlgebraicType;
    }
}