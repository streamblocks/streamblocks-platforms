package ch.epfl.vlsc.backend.cpp;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.type.Type;

@Module
public interface BasicChannels extends Channels {
	@Binding(BindingKind.INJECTED)
	Backend backend();

	default Emitter emitter() {
		return backend().emitter();
	}

	default void channelCodeForType(Type type, int size) {
		String tokenType = backend().code().type(type);
		emitter().emit("// CHANNEL %s", type);
		emitter().emit("typedef struct {");
		emitter().emit("	size_t head;");
		emitter().emit("	size_t tokens;");
		emitter().emit("	%s *buffer;", tokenType);
		emitter().emit("} channel_%s;", tokenType);
		emitter().emit("");

		emitter().emit("static inline _Bool channel_has_data_%s(channel_%1$s *channel, size_t tokens) {", tokenType);
		emitter().emit("	return channel->tokens >= tokens;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline _Bool channel_has_space_%s(channel_%1$s *channel_vector[], size_t channel_count, size_t tokens) {", tokenType);
		emitter().emit("	for (size_t i = 0; i < channel_count; i++) {");
		emitter().emit("		if (BUFFER_SIZE - channel_vector[i]->tokens < tokens) {");
		emitter().emit("			return false;");
		emitter().emit("		}");
		emitter().emit("	}");
		emitter().emit("	return true;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline void channel_write_one_%s(channel_%1$s *channel_vector[], size_t channel_count, %1$s data) {", tokenType);
		emitter().emit("	for (size_t c = 0; c < channel_count; c++) {");
		emitter().emit("		channel_%s *chan = channel_vector[c];", tokenType);
		emitter().emit("		chan->buffer[(chan->head + chan->tokens) %% BUFFER_SIZE] = data;");
		emitter().emit("		chan->tokens++;");
		emitter().emit("	}");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline void channel_write_%s(channel_%1$s *channel_vector[], size_t channel_count, %1$s *data, size_t tokens) {", tokenType);
		emitter().emit("	for (size_t c = 0; c < channel_count; c++) {");
		emitter().emit("		channel_%s *chan = channel_vector[c];", tokenType);
		emitter().emit("		for (size_t i = 0; i < tokens; i++) {");
		emitter().emit("			chan->buffer[(chan->head + chan->tokens) %% BUFFER_SIZE] = data[i];");
		emitter().emit("			chan->tokens++;");
		emitter().emit("		}");
		emitter().emit("	}");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline %s channel_peek_first_%1$s(channel_%1$s *channel) {", tokenType);
		emitter().emit("	return channel->buffer[channel->head];");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline void channel_peek_%s(channel_%1$s *channel, size_t offset, size_t tokens, %1$s *result) {", tokenType);
		emitter().emit("	%s *res = result;", tokenType);
		emitter().emit("	for (size_t i = 0; i < tokens; i++) {");
		emitter().emit("		res[i] = channel->buffer[(channel->head+i+offset) %% BUFFER_SIZE];");
		emitter().emit("	}");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline void channel_consume_%s(channel_%1$s *channel, size_t tokens) {", tokenType);
		emitter().emit("	channel->tokens -= tokens;");
		emitter().emit("	channel->head = (channel->head + tokens) %% BUFFER_SIZE;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static void channel_create_%1$s(channel_%1$s *channel) {", tokenType);
		emitter().emit("	%s *buffer = new %1$s[BUFFER_SIZE];", tokenType);
		emitter().emit("	channel->head = 0;");
		emitter().emit("	channel->tokens = 0;");
		emitter().emit("	channel->buffer = buffer;");
		emitter().emit("}");
		emitter().emit("");


		emitter().emit("static void channel_destroy_%s(channel_%1$s *channel) {", tokenType);
		emitter().emit("	free(channel->buffer);");
		emitter().emit("}");
		emitter().emit("");
	}

	default void inputActorCodeForType(Type type, int[] size) {
		String tokenType = backend().code().type(type);

		emitter().emit("typedef struct {");
		emitter().emit("	size_t channelc;");
		emitter().emit("	channel_%s **channelv;", tokenType);
		emitter().emit("	FILE *stream;");
		emitter().emit("} input_actor_%s;", tokenType);
		emitter().emit("");

		emitter().emit("static input_actor_%s *input_actor_create_%1$s(FILE *stream, channel_%1$s *channel_vector[], size_t channel_count) {", tokenType);
		emitter().emit("    input_actor_%s *actor = malloc(sizeof(input_actor_%1$s));", tokenType);
		emitter().emit("    actor->channelv = channel_vector;");
		emitter().emit("    actor->channelc = channel_count;");
		emitter().emit("    actor->stream = stream;");
		emitter().emit("    return actor;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static void input_actor_destroy_%s(input_actor_%1$s *actor) {", tokenType);
		emitter().emit("    free(actor);");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static _Bool input_actor_run_%s(input_actor_%1$s *actor) {", tokenType);
		emitter().emit("    size_t tokens = SIZE_MAX;");
		emitter().emit("    for (size_t i = 0; i < actor->channelc; i++) {");
		emitter().emit("        size_t s = BUFFER_SIZE - actor->channelv[i]->tokens;");
		emitter().emit("        tokens = s < tokens ? s : tokens;");
		emitter().emit("    }");
		emitter().emit("    if (tokens > 0) {");
		emitter().emit("        %s buf[tokens];", tokenType);
		emitter().emit("        tokens = fread(buf, sizeof(%s), tokens, actor->stream);", tokenType);
		emitter().emit("        if (tokens > 0) {");
		emitter().emit("            channel_write_%s(actor->channelv, actor->channelc, buf, tokens);", tokenType);
		emitter().emit("            return true;");
		emitter().emit("        } else {");
		emitter().emit("            return false;");
		emitter().emit("        }");
		emitter().emit("    } else {");
		emitter().emit("        return false;");
		emitter().emit("    }");
		emitter().emit("}");
		emitter().emit("");
	}

	default void outputActorCodeForType(Type type, int size) {
		String tokenType = backend().code().type(type);

		emitter().emit("typedef struct {");
		emitter().emit("	channel_%s *channel;", tokenType);
		emitter().emit("	FILE *stream;");
		emitter().emit("} output_actor_%s;", tokenType);
		emitter().emit("");

		emitter().emit("static output_actor_%s *output_actor_create_%1$s(FILE *stream, channel_%1$s *channel) {", tokenType);
		emitter().emit("    output_actor_%s *actor = malloc(sizeof(output_actor_%1$s));", tokenType);
		emitter().emit("    actor->channel = channel;");
		emitter().emit("    actor->stream = stream;");
		emitter().emit("    return actor;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static void output_actor_destroy_%s(output_actor_%1$s *actor) {", tokenType);
		emitter().emit("    free(actor);");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static _Bool output_actor_run_%s(output_actor_%1$s* actor) {", tokenType);
		emitter().emit("    channel_%s *channel = actor->channel;", tokenType);
		emitter().emit("    if (channel->tokens > 0) {");
		emitter().emit("        size_t wrap_or_end = channel->head + channel->tokens;");
		emitter().emit("        if (wrap_or_end > BUFFER_SIZE) {");
		emitter().emit("            wrap_or_end = BUFFER_SIZE;");
		emitter().emit("        }");
		emitter().emit("        size_t tokens_before_wrap = wrap_or_end - channel->head;");
		emitter().emit("        fwrite(&channel->buffer[channel->head], sizeof(%s), tokens_before_wrap, actor->stream);", tokenType);
		emitter().emit("");
		emitter().emit("        size_t tokens_after_wrap = channel->tokens - tokens_before_wrap;");
		emitter().emit("        if (tokens_after_wrap > 0) {");
		emitter().emit("            fwrite(&channel->buffer, sizeof(%s), tokens_after_wrap, actor->stream);", tokenType);
		emitter().emit("        }");
		emitter().emit("");
		emitter().emit("        //channel->head = (channel->head + channel->tokens) %% BUFFER_SIZE;");
		emitter().emit("        channel->tokens = 0;");
		emitter().emit("        return true;");
		emitter().emit("    } else {");
		emitter().emit("        return false;");
		emitter().emit("    }");
		emitter().emit("}");
		emitter().emit("");
	}
}
