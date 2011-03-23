package com.thebuzzmedia.hpjxp;

// TODO: Make an interface if it doesn't end up having any methods
public class Constants {
	public static final byte LT = 60; // <, less than
	public static final byte QM = 63; // ?, question mark
	public static final byte FS = 47; // /, forward slash
	public static final byte GT = 62; // >, greater than

	public static final byte SP = 32; // SPACE
	public static final byte TB = 9; // TAB
	public static final byte CR = 13; // \r, carriage return
	public static final byte LF = 10; // \n, line-feed (newline)
	public static final byte[] WS = { SP, TB, CR, LF }; // whitespace

	public static final byte[] NS = { 120, 109, 108, 110, 115 }; // xmlns
	public static final byte[] NS_CAP = { 88, 77, 76, 78, 83 }; // XMLNS

	public static final int INVALID = -1;
}