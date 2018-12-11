package ch.epfl.vlsc.backend.c.util;

import java.util.Objects;

/**
 * A mutable container for one element
 * @param <T> the type of content
 */
public final class Box<T> {
	private T content;
	private Box(T content) {
		this.content = content;
	}

	/**
	 * Creates a new container with the specified content
	 * @param content the content
	 * @param <T> the type of content
	 * @return a new Box with the given content
	 * @throws NullPointerException if content is null
	 */
	public static <T> Box<T> of(T content) {
		if (content == null) {
			throw new NullPointerException();
		}
		return new Box<>(content);
	}

	/**
	 * Creates a new container with the specified content, or an empty container if the content is null
	 * @param content the content for the box, or null
	 * @param <T> the type of content
	 * @return a new Box with the given content
	 */
	public static <T> Box<T> ofNullable(T content) {
		return new Box<>(content);
	}

	/**
	 * Creates an empty container.
	 * @param <T> the type of content
	 * @return an empty Box
	 */
	public static <T> Box<T> empty() {
		return new Box<>(null);
	}

	/**
	 * Returns true if the container is empty
	 * @return true if the container is empty
	 */
	public boolean isEmpty() {
		return content == null;
	}

	/**
	 * Returns the content if the box is not empty
	 * @return the content
	 * @throws IllegalStateException if the container is empty
	 */
	public T get() {
		if (isEmpty()) throw new IllegalStateException("Box.isEmpty()");
		return content;
	}

	/**
	 * Changes the content of the box
	 * @param content new content
	 * @throws NullPointerException if the given content is null
	 */
	public void set(T content) {
		if (content == null) throw new NullPointerException();
		this.content = content;
	}

	/**
	 * Empties the container
	 */
	public void clear() {
		content = null;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(content);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Box && Objects.equals(content, ((Box) obj).content);
	}

	@Override
	public String toString() {
		if (isEmpty()) {
			return "Box.empty()";
		} else {
			return String.format("Box.of(%s)", content);
		}
	}
}
