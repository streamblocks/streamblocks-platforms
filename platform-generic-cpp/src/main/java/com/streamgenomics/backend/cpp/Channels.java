package com.streamgenomics.backend.cpp;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.util.ImmutableEntry;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.Type;

import java.util.*;
import java.util.stream.Collectors;

@Module
public interface Channels {
	@Binding(BindingKind.INJECTED) Backend backend();
	default Emitter emitter() { return backend().emitter(); }

	void channelCodeForType(Type type, int size);
	void channelListCodeForType(Type type, int[] size);
	void inputActorCodeForType(Type type, int[] size);
	void outputActorCodeForType(Type type, int size);

	default void outputActorCode() {
		backend().task().getNetwork().getOutputPorts().stream()
				.map(backend().types()::declaredPortType)
				.distinct()
				.forEach((type) -> outputActorCodeForType(type, 0));
	}

	default void inputActorCode() {
		backend().task().getNetwork().getInputPorts().stream()
				.map(backend().types()::declaredPortType)
				.distinct()
				.forEach((type) -> inputActorCodeForType(type, new int[] {0}));
	}

	default void fifo_h() {
		emitter().emit("#include <stdint.h>");
		emitter().emit("");
		emitter().emitRawLine("#ifndef BUFFER_SIZE\n" +
				"#define BUFFER_SIZE 256\n" +
				"#endif\n");
		channelCode();
	}

	default Type alignedConnectionTypes(Connection connection) {
		Type type = backend().types().connectionType(backend().task().getNetwork(), connection);
		return intToNearest8Mult(type);
	}

	default int connectionBufferSize(Connection connection) {
		Optional<ToolValueAttribute> attribute = connection.getValueAttribute("buffersize");
		if (!attribute.isPresent()) {
			attribute = connection.getValueAttribute("bufferSize");
		}
		if (attribute.isPresent()) {
			return (int) backend().constants().intValue(attribute.get().getValue()).getAsLong();
		} else {
			return 0;
		}
	}

	default void channelCode() {
		Map<Type, Set<Integer>> buffers = backend().task().getNetwork().getConnections().stream()
				.collect(Collectors.groupingBy(
						this::alignedConnectionTypes,
						Collectors.mapping(
								this::connectionBufferSize,
								Collectors.toSet())));

		buffers.forEach((type, sizes) -> sizes.forEach(size -> {
			channelCodeForType(type, size);
		}));

		List<Map.Entry<Type, List<Integer>>> bufferLists = backend().task().getNetwork()
				.getConnections().stream()
				.collect(Collectors.groupingBy(Connection::getSource))
				.entrySet().stream()
				.map(entry -> ImmutableEntry.of(
						alignedConnectionTypes(entry.getValue().get(0)),
						entry.getValue().stream().map(this::connectionBufferSize).collect(Collectors.toList())))
				.distinct()
				.collect(Collectors.toList());

		bufferLists.forEach(entry -> {
			Type type = entry.getKey();
			int[] sizes = entry.getValue().stream().mapToInt(s -> s).toArray();
			channelListCodeForType(type, sizes);
		});
	}

	default Type intToNearest8Mult(Type t) {
		return t;
	}

	default IntType intToNearest8Mult(IntType t) {
		if (t.getSize().isPresent()) {
			int size = t.getSize().getAsInt();
			int limit = 8;
			while (size > limit) {
				limit = limit + limit;
			}
			return new IntType(OptionalInt.of(limit), t.isSigned());
		} else {
			return new IntType(OptionalInt.of(32), t.isSigned());
		}
	}

}
