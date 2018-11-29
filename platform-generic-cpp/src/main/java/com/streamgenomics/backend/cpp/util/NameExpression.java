package com.streamgenomics.backend.cpp.util;

import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class NameExpression {

	private NameExpression() {}

	public static Optional<NameExpression> decode(String text) {
		StringSource source = new StringSource(text);
		try {
			NameExpression expr = decode(source);
			source.assertEOF();
			return Optional.of(expr);
		} catch (StringSource.SourceException e) {
			return Optional.empty();
		}
	}

	public static Name name(String name) {
		return new Name(name);
	}

	public static Seq seq(NameExpression... exprs) {
		return new Seq(ImmutableList.of(exprs));
	}


	private static NameExpression decode(StringSource source) throws StringSource.SourceException {
		source.consumeChar('_');
		char type = source.getChar();
		switch (type) {
			case 'S': {
				int n = source.readNumber();
				ImmutableList.Builder<NameExpression> expr = ImmutableList.builder();
				while (n > 0) {
					expr.add(decode(source));
					n--;
				}
				return new Seq(expr.build());
			}
			case 'N': {
				int n = source.readNumber();
				source.consumeChar('_');
				String name = source.take(n);
				return new Name(name);
			}
			default: throw new StringSource.SourceException();
		}
	}


	public abstract String encode();

	public static final class Name extends NameExpression {
		private final String name;

		public Name(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		@Override
		public String encode() {
			return String.format("_N%d_%s", name.length(), name);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Name name1 = (Name) o;
			return Objects.equals(name, name1.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public static final class Seq extends NameExpression {
		private final ImmutableList<NameExpression> elements;

		public Seq(java.util.List<NameExpression> elements) {
			this.elements = ImmutableList.from(elements);
		}

		public ImmutableList<NameExpression> getElements() {
			return elements;
		}

		@Override
		public String encode() {
			String content = elements.stream().map(NameExpression::encode).collect(Collectors.joining());
			return String.format("_S%d%s", elements.size(), content);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Seq seq = (Seq) o;
			return Objects.equals(elements, seq.elements);
		}

		@Override
		public int hashCode() {
			return Objects.hash(elements);
		}

		@Override
		public String toString() {
			return elements.stream()
					.map(NameExpression::toString)
					.collect(Collectors.joining(" ", "(", ")"));
		}
	}
}
