package com.thebuzzmedia.hpjxp.buffer;

public interface IByteSource {
	public int getIndex();

	public int getLength();

	public byte[] getArray();

	public char[] decodeToChars();

	public String decodeToString();

	// TODO: To other native types?
}