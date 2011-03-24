package com.thebuzzmedia.hpjxp;

// TODO: Make an interface if it doesn't end up having any methods
public class Constants {
	// For clarity, define all the individual byte values for important chars.
	public static final byte LT = 60; // <, less than
	public static final byte QM = 63; // ?, question mark
	public static final byte EP = 33; // !, exclamation point
	public static final byte DA = 45; // -, dash (minus)
	public static final byte FS = 47; // /, forward slash
	public static final byte GT = 62; // >, greater than
	public static final byte LB = 91; // [, left bracket
	public static final byte RB = 93; // ], right bracket

	public static final byte[] A_LT = { LT }; // array, used for scan(byte[])
	public static final byte[] A_GT = { GT }; // array, used for scan(byte[])

	// <!--, comment start
	public static final byte[] CMT_PFX = { LT, EP, DA, DA };
	// -->, comment end
	public static final byte[] CMT_SFX = { DA, DA, GT };

	// <![CDATA[, CDATA start
	public static final byte[] CDATA_PFX = { LT, EP, LB, 67, 68, 65, 84, 65, LB };
	// ]]>, CDATA end
	public static final byte[] CDATA_SFX = { RB, RB, GT };

	// ?>, processing instruction end
	public static final byte[] PI_SFX = { QM, GT };

	// TODO: we might not need these anymore, need to evaluate.
	public static final byte SP = 32; // SPACE
	public static final byte TB = 9; // TAB
	public static final byte CR = 13; // \r, carriage return
	public static final byte LF = 10; // \n, line-feed (newline)
	public static final byte[] WS = { SP, TB, CR, LF }; // whitespace

	public static final byte[] NS = { 120, 109, 108, 110, 115 }; // xmlns
	public static final byte[] NS_CAP = { 88, 77, 76, 78, 83 }; // XMLNS

	public static final byte[] TAG_NAME_DELIM = { SP, FS, GT, TB, LF, CR };

	public static final int INVALID = -1;
}