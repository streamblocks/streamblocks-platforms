package ch.epfl.vlsc.backend.c;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.Type;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Module
public interface AlternativeChannels extends Channels {
	@Binding(BindingKind.INJECTED)
	Backend backend();

	default Emitter emitter() {
		return backend().emitter();
	}

	default void channelListCodeForType(Type type, int[] size) {
		String tokenType = backend().code().type(type);
		List<String> sizeStrings = Arrays.stream(size)
				.mapToObj(this::sizeToString)
				.collect(Collectors.toList());
		List<String> bufferSizes = Arrays.stream(size)
				.mapToObj(this::sizeToBufferSize)
				.collect(Collectors.toList());

		emitter().emit("typedef struct {");
		int index = 0;
		for (String sizeString : sizeStrings) {
			emitter().emit("	channel_%s_%s *channel_%d;", tokenType, sizeString, index);
			index += 1;
		}
		emitter().emit("");
		emitter().emit("} channel_list_%s_%s;", tokenType, String.join("_", sizeStrings));
		emitter().emit("");

		emitter().emit("static inline size_t channel_space_%s_%s(channel_list_%1$s_%2$s channel_list) {", tokenType, String.join("_", sizeStrings));
		index = 0;
		emitter().emit("	size_t min = SIZE_MAX;");
		for (String bufferSize : bufferSizes) {
			emitter().emit("	{");
			emitter().emit("		size_t s = %s - (channel_list.channel_%d->write - channel_list.channel_%2$d->read);", bufferSize, index);
			emitter().emit("		if (s < min) { min = s; }");
			emitter().emit("	}");
			index += 1;
		}
		emitter().emit("	return min;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline _Bool channel_has_space_%s_%s(channel_list_%1$s_%2$s channel_list, size_t tokens) {", tokenType, String.join("_", sizeStrings));
		index = 0;
		for (String bufferSize : bufferSizes) {
			emitter().emit("	if (%s - (channel_list.channel_%d->write - channel_list.channel_%2$d->read) < tokens) {", bufferSize, index);
			emitter().emit("		return false;");
			emitter().emit("	}");
			index += 1;
		}
		emitter().emit("	return true;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline void channel_write_one_%s_%s(channel_list_%1$s_%2$s channel_list, %1$s data) {", tokenType, String.join("_", sizeStrings));
		index = 0;
		for (int s : size) {
			emitter().emit("	{");
			emitter().emit("		channel_%s_%s *chan = channel_list.channel_%d;", tokenType, sizeToString(s), index);
			emitter().emit("		chan->buffer[chan->write %% %s] = data;", sizeToBufferSize(s));
			emitter().emit("		chan->write++;");
			emitter().emit("	}");
			index += 1;
		}
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline void channel_write_%s_%s(channel_list_%1$s_%2$s channel_list, %1$s *data, size_t tokens) {", tokenType, String.join("_", sizeStrings));
		index = 0;
		for (int s : size) {
			emitter().emit("	{");
			emitter().emit("		channel_%s_%s *chan = channel_list.channel_%d;", tokenType, sizeToString(s), index);
			emitter().emit("		for (size_t i = 0; i < tokens; i++) {");
			emitter().emit("			chan->buffer[chan->write %% %s] = data[i];", sizeToBufferSize(s));
			emitter().emit("			chan->write++;");
			emitter().emit("		}");
			emitter().emit("	}");
			index += 1;
		}
		emitter().emit("}");
		emitter().emit("");

	}

	default String sizeToBufferSize(int size) {
		return size == 0 ? "BUFFER_SIZE" : Integer.toString(size);
	}

	default String sourceEndTypeSize(Connection.End source) {
		Network network = backend().task().getNetwork();
		List<Connection> connections = network.getConnections().stream()
				.filter(conn -> conn.getSource().equals(source))
				.collect(Collectors.toList());
		Type type = backend().types().connectionType(network, connections.get(0));
		String size = connections.stream()
				.map(c -> sizeToString(connectionBufferSize(c)))
				.collect(Collectors.joining("_"));
		return backend().code().type(type) + "_" + size;
	}

	default String targetEndTypeSize(Connection.End target) {
		Network network = backend().task().getNetwork();
		Connection connection = network.getConnections().stream()
				.filter(conn -> conn.getTarget().equals(target))
				.findFirst().get();
		Type type = backend().types().connectionType(network, connection);
		String size = sizeToString(connectionBufferSize(connection));
		return backend().code().type(type) + "_" + size;
	}


	default String sizeToString(int size) {
		if (size == 0) {
			return "D";
		} else if (size > 0) {
			return Integer.toString(size);
		} else {
			throw new IllegalArgumentException();
		}
	}

	default void channelCodeForType(Type type, int size) {
		String tokenType = backend().code().type(type);
		String sizeString = sizeToString(size);
		String bufferSize = sizeToBufferSize(size);

		emitter().emit("// CHANNEL %s", type);
		emitter().emit("typedef struct {");
		emitter().emit("	size_t read;");
		emitter().emit("	size_t write;");
		emitter().emit("	%s *buffer;", tokenType);
		emitter().emit("} channel_%s_%s;", tokenType, sizeString);
		emitter().emit("");

		emitter().emit("static inline _Bool channel_has_data_%s_%s(channel_%1$s_%2$s *channel, size_t tokens) {", tokenType, sizeString);
		emitter().emit("	return channel->write - channel->read >= tokens;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline %s channel_peek_first_%1$s_%s(channel_%1$s_%2$s *channel) {", tokenType, sizeString);
		emitter().emit("	return channel->buffer[channel->read %% %s];", bufferSize);
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline void channel_peek_%s_%s(channel_%1$s_%2$s *channel, size_t offset, size_t tokens, %1$s *result) {", tokenType, sizeString);
		emitter().emit("	%s *res = result;", tokenType);
		emitter().emit("	for (size_t i = 0; i < tokens; i++) {");
		emitter().emit("		res[i] = channel->buffer[(channel->read+i+offset) %% %s];", bufferSize);
		emitter().emit("	}");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static inline void channel_consume_%s_%s(channel_%1$s_%2$s *channel, size_t tokens) {", tokenType, sizeString);
		emitter().emit("	channel->read += tokens;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static void channel_create_%s_%s(channel_%1$s_%2$s *channel) {", tokenType, sizeString);
		emitter().emit("	%s *buffer = malloc(sizeof(%1$s)*%s);", tokenType, bufferSize);
		emitter().emit("	channel->read = 0;");
		emitter().emit("	channel->write = 0;");
		emitter().emit("	channel->buffer = buffer;");
		emitter().emit("}");
		emitter().emit("");


		emitter().emit("static void channel_destroy_%s_%s(channel_%1$s_%2$s *channel) {", tokenType, sizeString);
		emitter().emit("	free(channel->buffer);");
		emitter().emit("}");
		emitter().emit("");
	}

	default void inputActorCodeForType(Type type, int[] size) {
		String tokenType = backend().code().type(type);
		List<String> sizeStrings = Arrays.stream(size)
				.mapToObj(this::sizeToString)
				.collect(Collectors.toList());
		List<String> bufferSizes = Arrays.stream(size)
				.mapToObj(this::sizeToBufferSize)
				.collect(Collectors.toList());
		String typeSize = tokenType + "_" + String.join("_", sizeStrings);

		emitter().emit("typedef struct {");
		emitter().emit("	channel_list_%s channel_list;", typeSize);
		emitter().emit("	FILE *stream;");
		emitter().emit("} input_actor_%s;", typeSize);
		emitter().emit("");

		emitter().emit("static input_actor_%s *input_actor_create_%1$s(FILE *stream, channel_list_%1$s channel_list) {", typeSize);
		emitter().emit("    input_actor_%s *actor = malloc(sizeof(input_actor_%1$s));", typeSize);
		emitter().emit("    actor->channel_list = channel_list;");
		emitter().emit("    actor->stream = stream;");
		emitter().emit("    return actor;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static void input_actor_destroy_%s(input_actor_%1$s *actor) {", typeSize);
		emitter().emit("    free(actor);");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static _Bool input_actor_run_%s(input_actor_%1$s *actor) {", typeSize);
		emitter().emit("	size_t space_before = channel_space_%s(actor->channel_list);", typeSize);
		emitter().emit("	size_t space = space_before;");
		emitter().emit("	while (space > 0 && !feof(actor->stream)) {");
		emitter().emit("		%s v[1024];", tokenType);
		emitter().emit("		size_t read = fread(&v, sizeof(%s), space > 1024 ? 1024 : space, actor->stream);", tokenType);
		emitter().emit("		channel_write_%s(actor->channel_list, v, read);", typeSize);
		emitter().emit("		space -= read;");
		emitter().emit("	}");
		emitter().emit("	return space != space_before;");
		emitter().emit("}");
		emitter().emit("");
	}

	default void outputActorCodeForType(Type type, int size) {
		String tokenType = backend().code().type(type);
		String typeSize = tokenType + "_" + sizeToString(size);

		emitter().emit("typedef struct {");
		emitter().emit("	channel_%s *channel;", typeSize);
		emitter().emit("	FILE *stream;");
		emitter().emit("} output_actor_%s;", typeSize);
		emitter().emit("");

		emitter().emit("static output_actor_%s *output_actor_create_%1$s(FILE *stream, channel_%1$s *channel) {", typeSize);
		emitter().emit("    output_actor_%s *actor = malloc(sizeof(output_actor_%1$s));", typeSize);
		emitter().emit("    actor->channel = channel;");
		emitter().emit("    actor->stream = stream;");
		emitter().emit("    return actor;");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static void output_actor_destroy_%s(output_actor_%1$s *actor) {", typeSize);
		emitter().emit("    free(actor);");
		emitter().emit("}");
		emitter().emit("");

		emitter().emit("static _Bool output_actor_run_%s(output_actor_%1$s* actor) {", typeSize);
		emitter().emit("    channel_%s *channel = actor->channel;", typeSize);
		emitter().emit("    if (channel->write > 0) {");
		emitter().emit("        fwrite(channel->buffer, sizeof(%s), channel->write, actor->stream);", tokenType);
		emitter().emit("        channel->write = 0;");
		emitter().emit("        return true;");
		emitter().emit("    } else {");
		emitter().emit("        return false;");
		emitter().emit("    }");
		emitter().emit("}");
		emitter().emit("");
	}
}
