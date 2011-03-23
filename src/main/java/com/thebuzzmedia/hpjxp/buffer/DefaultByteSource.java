package com.thebuzzmedia.hpjxp.buffer;

import com.thebuzzmedia.hpjxp.util.CodingUtil;

public class DefaultByteSource implements IByteSource {
	public static final char[] EMPTY_CHAR_ARRAY = new char[0];
	public static final String EMPTY_STRING = "";

	private int index;
	private int length;
	private byte[] array;

	public DefaultByteSource(byte[] array) {
		this(0, array);
	}

	public DefaultByteSource(int index, byte[] array) {
		this(index, (array == null ? 0 : array.length), array);
	}

	public DefaultByteSource(int index, int length, byte[] array)
			throws IllegalArgumentException {
		if (array == null)
			throw new IllegalArgumentException("array cannot be null");
		if (index < 0 || length < 0 || (index + length) > array.length)
			throw new IllegalArgumentException("index [" + index
					+ "] and length [" + length
					+ "] must be >= 0 and (index + length) ["
					+ (index + length) + "] must be <= array.length ["
					+ array.length + "]");

		this.index = index;
		this.length = length;
		this.array = array;
	}

	public String toString() {
		return this.getClass().getName()
				+ "[index="
				+ index
				+ ", length="
				+ length
				+ ", array="
				+ (array == null || length == 0 ? EMPTY_STRING : new String(
						array, index, length)) + "]";
	}

	public int getIndex() {
		return index;
	}

	public int getLength() {
		return length;
	}

	public byte[] getArray() {
		return array;
	}

	public byte[] copyArray() {
		byte[] copy = new byte[length];
		System.arraycopy(array, index, copy, 0, length);
		return copy;
	}

	public char[] decodeToChars() {
		return (length == 0 ? EMPTY_CHAR_ARRAY : CodingUtil.decode(index,
				length, array));
	}

	public String decodeToString() {
		return (length == 0 ? EMPTY_STRING : new String(array, index, length));
	}
}