package com.streamgenomics.backend.cpp.util;

import java.util.function.IntPredicate;

class StringSource {
	private final String s;
	private int pos;

	public StringSource(String text) {
		this.s = text;
	}

	public void assertEOF() throws SourceException {
		if (pos != s.length()) {
			throw new SourceException();
		}
	}

	public char getChar() throws SourceException {
		if (s.length() > pos) {
			return s.charAt(pos++);
		} else {
			throw new SourceException();
		}
	}

	public String take(int n) throws SourceException {
		if (pos + n <= s.length()) {
			String result = s.substring(pos, pos + n);
			pos += n;
			return result;
		} else {
			throw new SourceException();
		}
	}

	public void consumeChar(char c) throws SourceException {
		if (getChar() != c) {
			throw new SourceException();
		}
	}

	public int readNumber() throws SourceException {
		String numberText = takeWhile(Character::isDigit);
		try {
			return Integer.parseInt(numberText);
		} catch (NumberFormatException e) {
			throw new SourceException();
		}
	}

	public String takeWhile(IntPredicate predicate) {
		int start = pos;
		while (pos < s.length() && predicate.test(s.charAt(pos))) {
			pos++;
		}
		return s.substring(start, pos);
	}

	public static class SourceException extends Exception {}

}
