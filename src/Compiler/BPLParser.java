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
		// if there is a cached token, return that
		// otherwise get the next token from the scanner and set that as the next token
		this.firstToken = false;
		if (this.cachedToken != null) {
			this.currentToken = this.cachedToken;
			this.cachedToken = null;
			return this.currentToken;
		}
		this.scanner.getNextToken();
		this.currentToken = this.scanner.nextToken();
		return this.currentToken;
	}


	public void cacheToken() throws BPLParserException {
		if (this.cachedToken != null) {
			throw new BPLParserException("Kat you already cached it already you sillybutt!");
		}
		this.cachedToken = this.currentToken;
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
		BPLNode compoundStmt = this.compoundStmt();
		BPLNode program = new BPLNode("PROGRAM", compoundStmt.getLineNumber());
		program.addChild(compoundStmt);
		return program;
	}

	/**
	* grammar rule for compound statement node
	*/
	private BPLNode compoundStmt() throws BPLScannerException, BPLParserException {
		if (this.hasNextToken()) {
			Token token = this.getNextToken();
			if (token.getType() != Token.T_LCURLY) {
				throw new BPLParserException("Unexpected token type", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}

		BPLNode statementList = this.statementList();

		if (this.hasNextToken()) {
			Token token = this.getNextToken();
			if (token.getType() != Token.T_RCURLY) {
				throw new BPLParserException("Unexpected token type", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}

		BPLNode compoundStmt = new BPLNode("COMPOUND_STMT", statementList.getLineNumber());
		compoundStmt.addChild(statementList);

		return compoundStmt;
	}

	/**
	* grammar rule for statementList
	*/
	private BPLNode statementList() throws BPLScannerException, BPLParserException {
		Token token = this.getNextToken();
		if (token.getType() == Token.T_RCURLY) {
			cacheToken();
			return new BPLNode("EMPTY", token.getLineNumber());
		}
		cacheToken();

		BPLNode statement = this.statement();
		BPLNode statementList = new BPLNode("STATEMENT_LIST", statement.getLineNumber());
		statementList.addChild(statement);

		BPLNode sList = this.statementList();
		statementList.addChild(sList);

		return statementList;
	}

	/**
	* grammar rule for statement node
	*/
	private BPLNode statement() throws BPLScannerException, BPLParserException {
		if (!hasNextToken()) {
			throw new BPLParserException("More tokens expected.");
		}

		Token token = getNextToken();
		cacheToken();
		
		BPLNode node; 
		if (token.getType() == Token.T_LCURLY) { // compound statement
			node = this.compoundStmt();
		} else if (token.getType() == Token.T_IF) { // if statement
			node = this.ifStmt();
		} else if (token.getType() == Token.T_WHILE) { // while statement
			node = this.whileStmt();
		} else if (token.getType() == Token.T_RETURN) { // return statement
			node = this.returnStmt();
		} else if (token.getType() == Token.T_WRITE || token.getType() == Token.T_WRITELN) { // write statement
			node = this.writeStmt();
		} else {
			node = this.expressionStmt();
		}

		BPLNode statement = new BPLNode("STATEMENT", node.getLineNumber());
		statement.addChild(node);

		return statement;
	}

	/**
	* grammar rule for the while statement
	*/
	private BPLNode ifStmt() throws BPLScannerException, BPLParserException {
		Token ifTok;
		if (hasNextToken()) { // check to make sure there is a return token
			ifTok = getNextToken();
			if (ifTok.getType() != Token.T_IF) {
				throw new BPLParserException("Unexpected token.", ifTok.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}		

		BPLNode node = new BPLNode("IF_STMT", ifTok.getLineNumber());

		checkForLeftParen();

		BPLNode expres = this.expression();
		node.addChild(expres);

		checkForRightParen();

		BPLNode statement = this.statement();
		node.addChild(statement);

		if (hasNextToken()) {
			Token token = getNextToken();
			if (token.getType() != Token.T_ELSE) {
				cacheToken();
				return node;
			}
		} else {
			throw new BPLParserException("More tokens expected.");			
		}

		statement = this.statement();
		node.addChild(statement);

		return node;
	}	

	/**
	* grammar rule for the while statement
	*/
	private BPLNode whileStmt() throws BPLScannerException, BPLParserException {
		Token whileTok;
		if (hasNextToken()) { // check to make sure there is a return token
			whileTok = getNextToken();
			if (whileTok.getType() != Token.T_WHILE) {
				throw new BPLParserException("Unexpected token.", whileTok.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}

		BPLNode node = new BPLNode("WHILE_STMT", whileTok.getLineNumber());

		checkForLeftParen();

		BPLNode expres = this.expression();
		node.addChild(expres);

		checkForRightParen();

		BPLNode statement = this.statement();
		node.addChild(statement);

		return node;
	}

	/**
	* grammar rule for the write statements
	*/
	private BPLNode writeStmt() throws BPLScannerException, BPLParserException {
		Token writeTok;
		if (hasNextToken()) { // check to make sure there is a return token
			writeTok = getNextToken();
			if (writeTok.getType() != Token.T_WRITE && writeTok.getType() != Token.T_WRITELN) {
				throw new BPLParserException("Unexpected token.", writeTok.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}

		BPLNode node = new BPLNode("WRITE_STMT", writeTok.getLineNumber());

		checkForLeftParen();

		if (writeTok.getType() == Token.T_WRITE) {
			BPLNode expres = this.expression();
			node.addChild(expres);
		}

		checkForRightParen();
		checkForSemicolin();

		return node;
	}

	/**
	* grammar rule for the return statement
	*/
	private BPLNode returnStmt() throws BPLScannerException, BPLParserException {
		if (hasNextToken()) { // check to make sure there is a return token
			Token token = getNextToken();
			if (token.getType() != Token.T_RETURN) {
				throw new BPLParserException("Unexpected token.", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}

		if (!hasNextToken()) {
			throw new BPLParserException("More tokens expected.");
		}

		Token token = getNextToken();
		BPLNode returnStmt = new BPLNode("RETURN_STMT", token.getLineNumber());	
		if (token.getType() != Token.T_SEMICOL) { // if no semicolin, then there must be an expression
			cacheToken();
			BPLNode expression = this.expression();
			returnStmt.setLineNumber(expression.getLineNumber());				
			returnStmt.addChild(expression);
		} else {
			return returnStmt;
		}

		checkForSemicolin();

		return returnStmt;
	}

	/**
	* grammar rule for expression_stmt node
	*/
	private BPLNode expressionStmt() throws BPLScannerException, BPLParserException {
		if (hasNextToken()) {
			Token token = getNextToken();
			if (token.getType() == Token.T_SEMICOL) {
				return new BPLNode("EXPRESSION_STMT", token.getLineNumber());
			}
		}
		cacheToken();
		BPLNode expression = this.expression();
		BPLNode expressionStmt = new BPLNode("EXPRESSION_STMT", expression.getLineNumber());
		expressionStmt.addChild(expression);

		checkForSemicolin();

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
	* checks for and consumes a semicolin
	*/
	private void checkForSemicolin() throws BPLParserException, BPLScannerException {
		if (hasNextToken()) { // check to make sure there is a return token
			Token token = getNextToken();
			if (token.getType() != Token.T_SEMICOL) {
				throw new BPLParserException("Unexpected token.", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}
	}

	/**
	* checks for and consumes a right parenthesis
	*/
	private void checkForRightParen() throws BPLParserException, BPLScannerException {
		if (hasNextToken()) { // check to make sure there is a ) token
			Token token = getNextToken();
			if (token.getType() != Token.T_RPAREN) {
				throw new BPLParserException("Unexpected token.", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}
	}

	/**
	* checks for and consumes a left parenthesis
	*/
	private void checkForLeftParen() throws BPLParserException, BPLScannerException {
		if (hasNextToken()) { // check to make sure there is a ) token
			Token token = getNextToken();
			if (token.getType() != Token.T_LPAREN) {
				throw new BPLParserException("Unexpected token.", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected.");
		}
	}

	/**
	* returns the parse tree in a string form
	*/
	public String toString() {
		return this.toStringHelp(this.head, 0);
	}

	/**
	* helper for the toString function
	*/ 
	public String toStringHelp(BPLNode node, int depth) {
		String rtn = "";
		for (int i = 0; i < depth; i++) {
			rtn += "   ";
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