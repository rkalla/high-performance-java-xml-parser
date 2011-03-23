package com.thebuzzmedia.hpjxp.util;

import com.thebuzzmedia.hpjxp.Constants;

public class ScannerUtil {
	public static int indexOf(byte value, byte[] data) {
		return indexOf(value, 0, data.length, data);
	}

	public static int indexOf(byte value, int index, byte[] data) {
		return indexOf(value, index, (data.length - index), data);
	}

	public static int indexOf(byte value, int index, int length, byte[] data) {
		int result = index;

		for (int end = (index + length); result < end; result++) {
			if (data[result] == value)
				return result;
		}

		return Constants.INVALID;
	}

	public static int indexOf(byte[] values, byte[] data) {
		return indexOf(values, 0, data.length, data);
	}

	public static int indexOf(byte[] values, int index, byte[] data) {
		return indexOf(values, index, (data.length - index), data);
	}

	public static int indexOf(byte[] values, int index, int length, byte[] data) {
		int valuesLength = values.length;

		for (int end = (index + length); index < end; index++) {
			int j = 0;

			// Increment j as many times as we can match.
			for (; j < valuesLength && data[index + j] == values[j]; j++)
				;

			/*
			 * If j was incremented to match the length of values[], then we
			 * found a complete match, otherwise we didn't and we need to return
			 * to the outer loop to hunt some more, but we can at least skip the
			 * chars we tried to match already.
			 */
			if (j == valuesLength)
				return index;
			else
				index += j;
		}

		return Constants.INVALID;
	}

	public static int indexOfAny(byte[] values, byte[] data) {
		return indexOfAny(values, 0, data.length, data);
	}

	public static int indexOfAny(byte[] values, int index, byte[] data) {
		return indexOfAny(values, index, (data.length - index), data);
	}

	public static int indexOfAny(byte[] values, int index, int length,
			byte[] data) {
		int valuesLength = values.length;

		for (int end = (index + length); index < end; index++) {
			for (int j = 0, val = data[index]; j < valuesLength; j++) {
				if (val == values[j])
					return index;
			}
		}

		return Constants.INVALID;
	}
}