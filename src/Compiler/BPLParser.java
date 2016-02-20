package Compiler;

public class BPLParser {
	private final BPLScanner scanner;

	private Token currentToken;
	private Token cachedToken;
	private BPLNode head;
	private boolean firstToken;

	public BPLParser(String fileName) throws BPLScannerException, BPLParserException {
		this.scanner = new BPLScanner(fileName);
		this.firstToken = true;
		this.head = this.program();
	}

	/**
	* @return true if there is next token
	* @return false otherwise
	*/
	public boolean hasNextToken() {
		if (this.firstToken || (this.currentToken.getType() != Token.T_EOF)) {
			return true;
		}
		return false;
	}

	/**
	* gets the next token from the scanner
	* caches the previous token in case it is needed later
	* @return the current token
	*/
	public Token getNextToken() throws BPLScannerException, BPLParserException {
		this.firstToken = false;
		this.scanner.getNextToken();
		if (this.scanner.nextToken().getType() != Token.T_EOF) {
			this.cachedToken = this.currentToken;
		} else {
			this.cachedToken = null;
		}
		this.currentToken = this.scanner.nextToken();
		return this.currentToken;
	}

	/**
	* returns the head of the BLP tree
	*/
	public BPLNode getBPLHead() {
		return this.head;
	}

	/**
	* grammar rule for program node
	*/
	private BPLNode program() throws BPLScannerException, BPLParserException {
		BPLNode statement = this.statement();
		BPLNode program = new BPLNode("PROGRAM", statement.getLineNumber());
		program.addChild(statement);
		return program;
	}

	/**
	* grammar rule for statement node
	*/
	private BPLNode statement() throws BPLScannerException, BPLParserException {
		BPLNode expressionStmt = this.expressionStmt();
		BPLNode statement = new BPLNode("STATEMENT", expressionStmt.getLineNumber());
		statement.addChild(expressionStmt);
		return statement;
	}

	/**
	* grammar rule for expression_stmt node
	*/
	private BPLNode expressionStmt() throws BPLScannerException, BPLParserException {
		BPLNode expression = this.expression();
		BPLNode expressionStmt = new BPLNode("EXPRESSION_STMT", expression.getLineNumber());
		expressionStmt.addChild(expression);

		if (this.hasNextToken()) {
			Token token = this.getNextToken();
			if (token.getType() != Token.T_SEMICOL) {
				throw new BPLParserException("Unexpected token type", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}

		return expressionStmt;
	}

	/**
	* grammar rule for expression node
	*/
	private BPLNode expression() throws BPLScannerException, BPLParserException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected.");
		}

		Token token = this.getNextToken();
		if (token.getType() != Token.T_ID) {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}

		BPLVarNode node = new BPLVarNode(token.getValue(), token.getLineNumber());

		return node;
	}

	/**
	* returns the parse tree in a string form
	*/
	public String toString() {
		return this.toStringHelp(this.head, 0);
	}

	public String toStringHelp(BPLNode node, int depth) {
		String rtn = "";
		for (int i = 0; i < depth; i++) {
			rtn += "\t";
		}
		rtn += node.toString();
		for (int i = 0; i < node.getChildrenSize(); i++) {
			rtn += toStringHelp(node.getChild(i), depth + 1);
		}
		return rtn;
	}

	public static void main(String[] pirateArgs) throws BPLScannerException, BPLParserException {
		if (pirateArgs.length == 0) {
			System.err.println("File to parse needed!");
			System.exit(1);
		}
		String fileName = pirateArgs[0];
		BPLParser parser = new BPLParser(fileName);
		System.out.println(parser);
		System.exit(0);
	}
}