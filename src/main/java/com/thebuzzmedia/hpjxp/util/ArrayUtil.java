package com.thebuzzmedia.hpjxp.util;

public class ArrayUtil {
	public static byte[] ensureCapacity(int capacity, byte[] array) {
		return ensureCapacity(capacity, 1, array);
	}

	public static byte[] ensureCapacity(int capacity, float growthPercentage,
			byte[] array) {
		if (capacity < array.length)
			return array;

		int newCapacity = (int) ((float) array.length * (1f + growthPercentage));

		if (newCapacity < capacity)
			newCapacity = capacity;

		byte[] newArray = new byte[newCapacity];
		System.arraycopy(array, 0, newArray, 0, array.length);
		return newArray;
	}
}