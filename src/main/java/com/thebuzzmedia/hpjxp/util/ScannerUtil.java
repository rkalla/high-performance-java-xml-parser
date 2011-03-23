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
		int result = index;

		for (int end = (index + length); result < end; result++) {
			for (int j = 0, jLength = values.length, val = data[result]; j < jLength; j++) {
				if (val == values[j])
					return result;
			}
		}

		return Constants.INVALID;
	}
}