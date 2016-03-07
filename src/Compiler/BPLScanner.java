package Compiler;

import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;

public class BPLScanner {
	private String fileName;
	private Scanner scan;
	private Token curToken;
	private String currentLine;
	private int lineNumber;
	private int position;
	
	public BPLScanner(String fileName)  {
		this.fileName = fileName;
		this.position = 0;
		try {
			this.scan = new Scanner(new File(fileName));
		} catch (FileNotFoundException f) {
			f.printStackTrace();
			System.exit(-1);
		}
		this.currentLine = "";
		if (scan.hasNextLine()) {
			this.currentLine = scan.nextLine();
		}
		this.lineNumber = 1;
	}

	/**
	* returns the current token
	**/
	public Token nextToken() {
		return curToken;
	}

	/** 
	* get next token
	**/
	public void getNextToken() throws BPLScannerException {
		if (currentLine == "") {
			curToken = new Token("", Token.T_EOF, lineNumber, position);
			position++;
		}

		int i = 0;
		int j = 0;
		while (i < currentLine.length() && Character.isWhitespace(currentLine.charAt(i))) {
			i++;
		}

		if (i == currentLine.length()) {
			if (scan.hasNextLine()) { // new line
				currentLine = scan.nextLine();
				lineNumber++;
				getNextToken();
			} else { // end of file
				curToken = new Token("", Token.T_EOF, lineNumber, position);
				position++;
				currentLine = "";
			}
		} else {
			char c = currentLine.charAt(i);
			if (Character.isDigit(c)) {
				j = i+1;
				while (j < currentLine.length() && Character.isDigit(currentLine.charAt(j))) {
					j++;
				}
				String tokenString = currentLine.substring(i,j);
				curToken = new Token(tokenString, Token.T_NUM, lineNumber, position);
				position++;
				currentLine = currentLine.substring(j);
			} else if (Character.isLetter(c)) {
				j = i+1;
				while (j < currentLine.length() && (Character.isLetterOrDigit(currentLine.charAt(j)) || (currentLine.charAt(j) == '_'))) {					
					j++;
				}
				String tokenString = currentLine.substring(i,j);
				if (tokenString.equals("int")) {
					curToken = new Token(tokenString, Token.T_INT, lineNumber, position);
					position++;
				} else if (tokenString.equals("void")) {
					curToken = new Token(tokenString, Token.T_VOID, lineNumber, position);
					position++;
				} else if (tokenString.equals("string")) {
					curToken = new Token(tokenString, Token.T_STRING, lineNumber, position);
					position++;
				} else if (tokenString.equals("if")) {
					curToken = new Token(tokenString, Token.T_IF, lineNumber, position);
					position++;
				} else if (tokenString.equals("else")) {
					curToken = new Token(tokenString, Token.T_ELSE, lineNumber, position);
					position++;
				} else if (tokenString.equals("while")) {
					curToken = new Token(tokenString, Token.T_WHILE, lineNumber, position);
					position++;
				} else if (tokenString.equals("write")) {
					curToken = new Token(tokenString, Token.T_WRITE, lineNumber, position);
					position++;
				} else if (tokenString.equals("writeln")) {
					curToken = new Token(tokenString, Token.T_WRITELN, lineNumber, position);
					position++;
				} else if (tokenString.equals("return")) {
					curToken = new Token(tokenString, Token.T_RETURN, lineNumber, position);
					position++;
				} else if (tokenString.equals("read")) {
					curToken = new Token(tokenString, Token.T_READ, lineNumber, position);
					position++;
				} else {
					curToken = new Token(tokenString, Token.T_ID, lineNumber, position);
					position++;
				}
				currentLine = currentLine.substring(j);
			} else if (c == '\"') {
				// assuming strings can only be on one line
				j = i + 1;
				while (currentLine.charAt(j) != '\"') {
					if (j == currentLine.length() - 1) {
						throw new BPLScannerException("Expected '\"'' missing", lineNumber);
					}
					j++;
				}
				j++;
				String tokenString = currentLine.substring(i,j);
				curToken = new Token(tokenString, Token.T_REALSTRING, lineNumber, position);
				position++;
				currentLine = currentLine.substring(j);
			} else if (c == '/') { // should be a comment and we need to skip
				char ch = currentLine.charAt(i);
				// if there is no star after /, then it is just a / token
				if (i+1 >= currentLine.length() || (i+1 < currentLine.length() && currentLine.charAt(i+1) != '*')) {
					curToken = new Token("/", Token.T_BACKSLASH, lineNumber, position);
					position++;
					currentLine = currentLine.substring(i+1);
					return;
				} else { // found a star; this is a comment we need to skip
					i += 2;
					if (i >= currentLine.length()) {
						currentLine = findNextNonEmptyLine(currentLine);
						i = 0;
					}
					ch = currentLine.charAt(i);
				}
				while (true) {
					// if the current charater is '*' and the next charater is '/', then the comment is done.
					if ((ch == '*') && (i < currentLine.length() - 1) && (currentLine.charAt(i+1) == '/')) {
						if (i+1 < currentLine.length()) {
							currentLine = currentLine.substring(i+2);
							getNextToken();
						} else if (scan.hasNextLine()) {
							currentLine = scan.nextLine();
							lineNumber++;
							getNextToken();
						} else {
							throw new BPLScannerException("Expected '*/' missing", lineNumber);
						}
						return;
					}

					// if we have reached the end of the current line, then move on to the next line
					if (i + 1 >= currentLine.length()) {
						if (scan.hasNextLine()) {
							currentLine = findNextNonEmptyLine(currentLine);
							i = -1;
						} else {
							throw new BPLScannerException("Expected '*/' missing", lineNumber);
						}
					}

					// we are not at the end of the current line; keep going
					i++;
					if (i >= currentLine.length()) {
						throw new BPLScannerException("Expected '*/' missing", lineNumber);
					}
					ch = currentLine.charAt(i);
				}
			}else if (c == '-') {
				curToken = new Token ("-", Token.T_MINUS, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '+') {
				curToken = new Token ("+", Token.T_PLUS, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == ';') {
				curToken = new Token (";", Token.T_SEMICOL, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '(') {
				curToken = new Token ("(", Token.T_LPAREN, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == ')') {
				curToken = new Token (")", Token.T_RPAREN, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '{') {
				curToken = new Token ("{", Token.T_LCURLY, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '}') {
				curToken = new Token ("}", Token.T_RCURLY, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '[') {
				curToken = new Token ("[", Token.T_LSQUARE, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == ']') {
				curToken = new Token ("]", Token.T_RSQUARE, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '*') {
				curToken = new Token ("*", Token.T_STAR, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '%') {
				curToken = new Token ("%", Token.T_PERCENT, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '&') {
				curToken = new Token ("&", Token.T_AMPER, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == ',') {
				curToken = new Token (",", Token.T_COMMA, lineNumber, position);
				position++;
				currentLine = currentLine.substring(i+1);
			} else if (c == '=') {
				if ((i+1 < currentLine.length()) && currentLine.charAt(i+1) == '=') {
					curToken = new Token ("==", Token.T_EQCOMP, lineNumber, position);
					position++;
					currentLine = currentLine.substring(i+2);
				} else {
					curToken = new Token ("=", Token.T_EQ, lineNumber, position);
					position++;
					currentLine = currentLine.substring(i+1);
				}
			} else if (c == '<') {
				if ((i+1 < currentLine.length()) && currentLine.charAt(i+1) == '=') {
					curToken = new Token ("<=", Token.T_LEQ, lineNumber, position);
					position++;
					currentLine = currentLine.substring(i+2);
				} else {
					curToken = new Token ("<", Token.T_LESS, lineNumber, position);
					position++;
					currentLine = currentLine.substring(i+1);
				}
			} else if (c == '>') {
				if ((i+1 < currentLine.length()) && currentLine.charAt(i+1) == '=') {
					curToken = new Token (">=", Token.T_GEQ, lineNumber, position);
					position++;
					currentLine = currentLine.substring(i+2);
				} else {
					curToken = new Token (">", Token.T_GREAT, lineNumber, position);
					position++;
					currentLine = currentLine.substring(i+1);
				}
			} else if (c == '!') {
				if ((i+1 < currentLine.length()) && currentLine.charAt(i+1) == '=') {
					curToken = new Token ("!=", Token.T_NEQ, lineNumber, position);
					position++;
					currentLine = currentLine.substring(i+2);
				} else {
					throw new BPLScannerException("Expected '=' after '!'", lineNumber);
				}
			}
		}
	}

	/**
	* finds the next non empty line and returns it
	* used by getNextToken
	*/
	private String findNextNonEmptyLine(String currentLine) throws BPLScannerException {
		while (scan.hasNextLine()) {
			currentLine = scan.nextLine();
			lineNumber++;
			if (currentLine.length() != 0) {
				return currentLine;
			}
		}
		throw new BPLScannerException("Expected '*/' missing", lineNumber);
	}

	public static void main(String[] args) throws BPLScannerException {
		String inputFileName;
		BPLScanner myScanner;

		if (args.length == 0) {
			throw new BPLScannerException("No file given");
		}

		inputFileName = args[0];
		
		myScanner = new BPLScanner("../" + inputFileName);
		myScanner.getNextToken();
		while (myScanner.nextToken().getType() != Token.T_EOF) {
			System.out.println(myScanner.nextToken());
			myScanner.getNextToken();
		}
		System.exit(0);
	}
}