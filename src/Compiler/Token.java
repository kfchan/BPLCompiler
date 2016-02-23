package Compiler;

public class Token {
	public static final int T_EOF = 100;
	public static final int T_IF = 101;
	public static final int T_ELSE = 102;
	public static final int T_WHILE = 103;
	public static final int T_ID = 104;
	public static final int T_NUM = 105;
	public static final int T_MINUS = 106; // -
	public static final int T_SEMICOL = 107; // ;
	public static final int T_LPAREN = 108; // (
	public static final int T_RPAREN = 109; // )
	public static final int T_LCURLY = 110; // {
	public static final int T_RCURLY = 111; // }
	public static final int T_LSQUARE = 112; // [
	public static final int T_RSQUARE = 113; // ]
	public static final int T_LESS = 114; // <
	public static final int T_GREAT = 115; // >
	public static final int T_COMMA = 116; // ,
	public static final int T_GEQ = 117; // >=
	public static final int T_LEQ = 118; // <=
	public static final int T_NEQ = 119; // !=
	public static final int T_EQCOMP = 120; // ==
	public static final int T_PLUS = 121; // +
	public static final int T_STAR = 122; // *
	public static final int T_PERCENT = 123; // %
	public static final int T_BACKSLASH = 124;
	public static final int T_STRING = 125; 
	public static final int T_AMPER = 126; // &
	public static final int T_WRITE = 127;
	public static final int T_READ = 128;
	public static final int T_INT = 129;
	public static final int T_RETURN = 130;
	public static final int T_VOID = 131;
	public static final int T_EQ = 132; // =
	public static final int T_WRITELN = 133;
	public static final int T_REALSTRING = 134;

	private final String tString;
	private final int type;
	private final int lineNumber;

	public Token(String token, int type, int lineNum) {
		this.tString = token;
		this.type = type;
		this.lineNumber = lineNum;
	}

	public String getValue() {
		return this.tString;
	}

	public int getType() {
		return this.type;
	}

	public int getLineNumber() {
		return this.lineNumber;
	}

	public String toString() {
		return "Token " + this.type + ", string '" + this.tString + "', line number " + this.lineNumber;
	}
}