package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.hls.backend.directives.*;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Annotation;

@Module
public interface Annotations {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }


    default Directive parse(Annotation annotation) {
        String name = annotation.getName();
        Directives directives = Directives.directive(name);

        switch (directives) {
            case ARRAY_PARTITION:
                return ArrayPartition.parse(backend(), annotation);
            case INLINE:
                return InlineDirective.parse(backend().interpreter(), annotation);
            case LATENCY:
                return LatencyDirective.parse(backend().interpreter(), annotation);
            case LOOP_FLATTEN:
                return LoopFlattenDirective.parse(backend().interpreter(), annotation);
            case LOOP_MERGE:
                return LoopMergeDirective.parse(backend().interpreter(), annotation);
            case LOOP_TRIPCOUNT:
                return LoopTripCountDirective.parse(backend().interpreter(), annotation);
            case PIPELINE:
                return PipelineDirective.parse(backend().interpreter(), annotation);
            case UNROLL:
                return UnrollDirective.parse(backend().interpreter(), annotation);
            default:
                return new NullDirective();
        }
    }



    default void emitTopAction(Annotation annotation) {
        Directive directive;

        switch (Directives.directive(annotation.getName())) {
            case ARRAY_PARTITION:
                directive =  ArrayPartition.parse(backend(), annotation);
                break;
            case INLINE:
                directive = InlineDirective.parse(backend().interpreter(), annotation);
                break;
            case LATENCY:
                directive = LatencyDirective.parse(backend().interpreter(), annotation);
                break;
            case LOOP_MERGE:
                directive = LoopMergeDirective.parse(backend().interpreter(), annotation);
                break;
            case PIPELINE:
                directive =  PipelineDirective.parse(backend().interpreter(), annotation);
                break;
            default:
                directive = new NullDirective();
        }
        if (!(directive instanceof NullDirective)) {
            emitter().emit("#pragma HLS %s", directive.toString());
        }
    }

    default void emitInsideCode(Annotation annotation) {
        Directive directive;

        switch (Directives.directive(annotation.getName())) {
            case LATENCY:
                directive = LatencyDirective.parse(backend().interpreter(), annotation);
                break;
            case LOOP_FLATTEN:
                directive = LoopFlattenDirective.parse(backend().interpreter(), annotation);
                break;
            case LOOP_MERGE:
                directive = LoopMergeDirective.parse(backend().interpreter(), annotation);
                break;
            case LOOP_TRIPCOUNT:
                directive =  LoopTripCountDirective.parse(backend().interpreter(), annotation);
                break;
            case PIPELINE:
                directive =  PipelineDirective.parse(backend().interpreter(), annotation);
                break;
            case UNROLL:
                directive = UnrollDirective.parse(backend().interpreter(), annotation);
                break;
            default:
                directive = new NullDirective();
        }
        if (!(directive instanceof NullDirective)) {
            emitter().emit("#pragma HLS %s", directive.toString());
        }
    }

    default void emitTop(Annotation annotation) {
        Directive directive;

        switch (Directives.directive(annotation.getName())) {
            case INLINE:
                directive = InlineDirective.parse(backend().interpreter(), annotation);
                break;
            case LATENCY:
                directive = LatencyDirective.parse(backend().interpreter(), annotation);
                break;
            case LOOP_FLATTEN:
                directive = LoopFlattenDirective.parse(backend().interpreter(), annotation);
                break;
            case LOOP_MERGE:
                directive = LoopMergeDirective.parse(backend().interpreter(), annotation);
                break;
            case PIPELINE:
                directive =  PipelineDirective.parse(backend().interpreter(), annotation);
                break;
            default:
                directive = new NullDirective();
        }

        if (!(directive instanceof NullDirective)) {
            emitter().emit("#pragma HLS %s", directive.toString());
        }
    }

    default void emitInstance(Annotation annotation) {
        Directive directive;

        switch (Directives.directive(annotation.getName())) {
            case ARRAY_PARTITION:
                directive =  ArrayPartition.parse(backend(), annotation);
                break;
            default:
                directive = new NullDirective();
        }

        if (!(directive instanceof NullDirective)) {
            emitter().emit("#pragma HLS %s", directive.toString());
        }
    }

}
