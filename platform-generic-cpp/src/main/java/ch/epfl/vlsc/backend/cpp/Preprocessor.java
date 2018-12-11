package ch.epfl.vlsc.backend.cpp;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;

@Module
public interface Preprocessor {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    // ------------------------------------------------------------------------
    // -- IF DEF

    default void ifndef(String token) {
        emitter().emit("#ifndef __%s__", token.toUpperCase());
    }

    default void define(String token) {
        emitter().emit("#define __%s__", token.toUpperCase());
        emitter().emit("");
    }

    default void defineDeclaration(String name, String value){
        emitter().emit("#define %s %s", name, value);
    }

    default void endif() {
        emitter().emit("#endif");
    }

    // ------------------------------------------------------------------------
    // -- INCLUDES

    default void systemInclude(String header) {
        emitter().emit("#include <%s>", header);
    }

    default void userInclude(String header) {
        emitter().emit("#include \"%s\"", header);
    }

    default void userIncludeActor(String name){
        userInclude("actor_" + name + ".h");
    }

    // ------------------------------------------------------------------------
    // -- PRAGMAS

    default void pragma(String token) {
        emitter().emit("#pragma %s", token);
    }

}
