package com.thebuzzmedia.hpjxp.buffer;

public interface IByteSource {
	public int getIndex();

	public int getLength();

	public byte[] getArray();
	
	public byte[] copyArray();

	public char[] decodeToChars();

	public String decodeToString();
	
	// TODO: Check XML spec, but entity-replacement may be mandatory,
	// scanning for &blah; entities and replacing them with their real values.

	// TODO: To other native types?
}