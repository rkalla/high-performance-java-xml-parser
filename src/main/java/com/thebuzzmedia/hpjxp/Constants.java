package com.thebuzzmedia.hpjxp;

// TODO: Make an interface if it doesn't end up having any methods
public class Constants {
	public static final byte LT = 60; // <, less than
	public static final byte QM = 63; // ?, question mark
	public static final byte EP = 33; // !, exclamation point
	public static final byte DA = 45; // -, dash (minus)
	public static final byte FS = 47; // /, forward slash
	public static final byte GT = 62; // >, greater than
	public static final byte LB = 91; // [, left bracket
	public static final byte RB = 93; // ], right bracket
	
	public static final byte[] C_START = {LT, EP, DA, DA}; // <!-- comment
	public static final byte[] C_END = {DA, DA, GT}; // -->
	// CDATA start, <![CDATA[
	public static final byte[] CD_START = { LT, EP, LB, 67, 68, 65, 84, 65, LB };
	// CDATA end, ]]>
	public static final byte[] CD_END = { RB, RB, GT };
	
	public static final byte SP = 32; // SPACE
	public static final byte TB = 9; // TAB
	public static final byte CR = 13; // \r, carriage return
	public static final byte LF = 10; // \n, line-feed (newline)
	public static final byte[] WS = { SP, TB, CR, LF }; // whitespace

	public static final byte[] NS = { 120, 109, 108, 110, 115 }; // xmlns
	public static final byte[] NS_CAP = { 88, 77, 76, 78, 83 }; // XMLNS

	public static final byte[] TN_TERM = { SP, FS, GT, TB, LF, CR };

	public static final int INVALID = -1;
}