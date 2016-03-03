package Compiler;

import java.util.*;

public class BPLParser {
	private final BPLScanner scanner;

	private Token currentToken;
	private BPLNode head;
	private boolean firstToken;
	private LinkedList<Token> cachedTokens;

	public BPLParser(String fileName) throws BPLScannerException, BPLParserException {
		this.scanner = new BPLScanner(fileName);
		this.cachedTokens = new LinkedList<Token>();
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
		if (!this.cachedTokens.isEmpty()) {
			this.currentToken = this.cachedTokens.poll();
			return this.currentToken;
		}
		this.scanner.getNextToken();
		this.currentToken = this.scanner.nextToken();
		return this.currentToken;
	}

	/**
	* caches the token so that it can be gotten again
	*/ 
	public void cacheToken() throws BPLParserException {
		this.cacheThisToken(this.currentToken);
	}

	/**
	* caches specified token (for funDec/varDec)
	*/
	public void cacheThisToken(Token token) {
		cachedTokens.addLast(token);
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
		BPLNode declarationList = this.declarationList();
		BPLNode program = new BPLNode("PROGRAM", declarationList.getLineNumber());
		program.addChild(declarationList);
		return program;
	}

	/**
	* grammar rule for declaration list node
	*/	
	private BPLNode declarationList() throws BPLScannerException, BPLParserException {
		BPLNode declaration = this.declaration();
		BPLNode declarationList = new BPLNode("DECLARATION_LIST", declaration.getLineNumber());	

		if (hasNextToken()) {
			Token token = this.getNextToken();
			if (token.getType() == Token.T_EOF) {
				declarationList.addChild(declaration);
				return declarationList;
			}
			cacheToken();
		}

		BPLNode declarationListChild = this.declarationList();
		declarationList.addChild(declarationListChild);
		declarationList.addChild(declaration);
		return declarationList;
	}

	/**
	* grammar rule for declaration node
	*/
	private BPLNode declaration() throws BPLScannerException, BPLParserException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token1 = this.getNextToken(); // should be a type specifier
		if ((token1.getType() != Token.T_INT) && (token1.getType() != Token.T_VOID) && (token1.getType() != Token.T_STRING)) {
			throw new BPLParserException("Unexpected token type", token1.getLineNumber());
		}

		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token2 = this.getNextToken();
		if (token2.getType() == Token.T_STAR) {
			// var_dec
			cacheThisToken(token1);
			cacheThisToken(token2);
			BPLNode varDec = this.varDec();
			BPLNode dec = new BPLNode("DECLARATION", token1.getLineNumber());
			dec.addChild(varDec);
			return dec;
		} else if (token2.getType() != Token.T_ID) {
			throw new BPLParserException("Unexpected token type", token2.getLineNumber());
		}

		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		BPLNode child;
		Token token3 = this.getNextToken();
		if ((token3.getType() == Token.T_SEMICOL) || (token3.getType() == Token.T_LSQUARE)) {
			// var_dec
			cacheThisToken(token1);
			cacheThisToken(token2);
			cacheThisToken(token3);	
			child = this.varDec();		
		} else if (token3.getType() == Token.T_LPAREN) {
			// fun_dec
			cacheThisToken(token1);
			cacheThisToken(token2);
			cacheThisToken(token3);
			child = this.funDec();
		} else {
			throw new BPLParserException("Unexpected token type", token3.getLineNumber());
		}

		BPLNode dec = new BPLNode("DECLARATION", token1.getLineNumber());
		dec.addChild(child);
		return dec;
	}

	/**
	* grammar rule for compound statement node
	*/
	private BPLNode funDec() throws BPLScannerException, BPLParserException {
		BPLNode type = this.typeSpecifier();

		Token token;
		if (hasNextToken()) {
			token = getNextToken();
			if (token.getType() != Token.T_ID) {
				throw new BPLParserException("Unexpected token type", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
		}		

		BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());

		checkForLeftParen();

		// TODO: PARAMS

		checkForRightParen();

		BPLNode compoundStmt = this.compoundStmt();

		BPLNode funDec = new BPLNode("FUN_DEC", type.getLineNumber());
		funDec.addChild(type);
		funDec.addChild(id);
		// funDec.addChild(params);
		funDec.addChild(compoundStmt);
		return funDec;
	}


	/**
	* grammar rule for compound statement node
	*/
	private BPLNode compoundStmt() throws BPLScannerException, BPLParserException {
		Token lcurly;
		if (this.hasNextToken()) {
			lcurly = this.getNextToken();
			if (lcurly.getType() != Token.T_LCURLY) {
				throw new BPLParserException("Unexpected token type", lcurly.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
		}

		BPLNode localDs  = this.localDecs();
		BPLNode statementList = this.statementList();

		if (this.hasNextToken()) {
			Token token = this.getNextToken();
			if (token.getType() != Token.T_RCURLY) {
				throw new BPLParserException("Unexpected token type", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
		}

		BPLNode compoundStmt = new BPLNode("COMPOUND_STMT", lcurly.getLineNumber());
		compoundStmt.addChild(localDs);
		compoundStmt.addChild(statementList);

		return compoundStmt;
	}

	/**
	* grammar for local declarations
	**/
	private BPLNode localDecs() throws BPLScannerException, BPLParserException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}
		Token token = this.getNextToken();
		if ((token.getType() != Token.T_INT) && (token.getType() != Token.T_VOID) && (token.getType() != Token.T_STRING)) {
			cacheToken();
			return new BPLNode("<empty>", token.getLineNumber());
		}
		cacheToken();

		BPLNode varD = this.varDec();
		BPLNode localD = new BPLNode("LOCAL_DECS", varD.getLineNumber());
		BPLNode localDs = this.localDecs();
		
		// add children
		localD.addChild(localDs);
		localD.addChild(varD);
		

		return localD;
	} 

	/**
	* grammar rule for variable declaration
	*/
	private BPLNode varDec() throws BPLScannerException, BPLParserException {
		// get type specifier
		BPLNode type = this.typeSpecifier();
		BPLNode dec = new BPLNode("VAR_DEC", type.getLineNumber());

		if (!this.hasNextToken()) {
 			throw new BPLParserException("More tokens expected", type.getLineNumber());
		}

		Token token = this.getNextToken();
		if (token.getType() == Token.T_STAR) { // if next token is star, then there must be an id that follows
			BPLNode star = new BPLNode("*", token.getLineNumber());
			dec.addChild(star);

			if (this.hasNextToken()) {
				token = this.getNextToken();
				if (token.getType() != Token.T_ID) {
					throw new BPLParserException("ID missing after '*'", token.getLineNumber());
				}
				BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
				dec.addChild(id);
			} else {
				throw new BPLParserException("More tokens expected", token.getLineNumber());
			}
		} else if (token.getType() == Token.T_ID) { // if id token is next, then also check for [] tokens
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			dec.addChild(id);

			dec = varDecBracketHelper(dec);
		} else {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}

		// check for semicolins
		checkForSemicolin();

		return dec;
	}

	/**
	* checks to see if there is a left bracket
	* if there isn't, return
	* if there is, check for int and right bracket
	*/
	private BPLNode varDecBracketHelper(BPLNode dec) throws BPLScannerException, BPLParserException {
		// check for the left bracket
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected", dec.getLineNumber());
		}
		Token token = this.getNextToken();
		if (token.getType() != Token.T_LSQUARE) { // if no left bracket, then cache Token and return
			cacheToken();
			return dec;
		}
		BPLNode leftBracket = new BPLNode("LSQUARE_BR", token.getLineNumber());
		dec.addChild(leftBracket);

		// check for integer between the brackets
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected", dec.getLineNumber());
		}
		token = this.getNextToken();
		if (token.getType() != Token.T_NUM) {
			throw new BPLParserException("Integer expected after '['", dec.getLineNumber());
		}
		int val = Integer.parseInt(token.getValue());
		BPLNode integer = new BPLIntegerNode(val, token.getLineNumber());
		dec.addChild(integer);

		// check for the right bracket
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected", dec.getLineNumber());
		}
		token = this.getNextToken();
		if (token.getType() != Token.T_RSQUARE) {
			throw new BPLParserException("Missing token ']'", dec.getLineNumber());
		}
		BPLNode rightBracket = new BPLNode("RSQUARE_BR", token.getLineNumber());
		dec.addChild(rightBracket);

		return dec;
	}

	/**
	* grammar rule for the type specifier node
	*/
	private BPLNode typeSpecifier() throws BPLScannerException, BPLParserException {
		if (this.hasNextToken()) {
			Token token = this.getNextToken();
			if (token.getType() == Token.T_INT) {
				return new BPLNode("int", token.getLineNumber());
			} else if (token.getType() == Token.T_VOID) {
				return new BPLNode("void", token.getLineNumber());
			} else if (token.getType() == Token.T_STRING) {
				return new BPLNode("string", token.getLineNumber());
			} else {
				throw new BPLParserException("Unexpected token type", token.getLineNumber());
			}
		}
		throw new BPLParserException("More tokens expected");		
	}

	/**
	* grammar rule for statementList
	*/
	private BPLNode statementList() throws BPLScannerException, BPLParserException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}
		Token token = this.getNextToken();
		if (token.getType() == Token.T_RCURLY) {
			cacheToken();
			return new BPLNode("<empty>", token.getLineNumber());
		}
		cacheToken();

		BPLNode statement = this.statement();
		BPLNode statementList = new BPLNode("STATEMENT_LIST", statement.getLineNumber());
		BPLNode sList = this.statementList();

		// add children
		statementList.addChild(sList);
		statementList.addChild(statement);

		return statementList;
	}

	/**
	* grammar rule for statement node
	*/
	private BPLNode statement() throws BPLScannerException, BPLParserException {
		if (!hasNextToken()) {
			throw new BPLParserException("More tokens expected");
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
				throw new BPLParserException("Unexpected token", ifTok.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
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
			throw new BPLParserException("More tokens expected");			
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
				throw new BPLParserException("Unexpected token", whileTok.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
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
				throw new BPLParserException("Unexpected token", writeTok.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
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
				throw new BPLParserException("Unexpected token", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
		}

		if (!hasNextToken()) {
			throw new BPLParserException("More tokens expected");
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
			throw new BPLParserException("Expression expected");
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
				throw new BPLParserException("Unexpected token", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
		}
	}

	/**
	* checks for and consumes a right parenthesis
	*/
	private void checkForRightParen() throws BPLParserException, BPLScannerException {
		if (hasNextToken()) { // check to make sure there is a ) token
			Token token = getNextToken();
			if (token.getType() != Token.T_RPAREN) {
				throw new BPLParserException("Unexpected token", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
		}
	}

	/**
	* checks for and consumes a left parenthesis
	*/
	private void checkForLeftParen() throws BPLParserException, BPLScannerException {
		if (hasNextToken()) { // check to make sure there is a ) token
			Token token = getNextToken();
			if (token.getType() != Token.T_LPAREN) {
				throw new BPLParserException("Unexpected token", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
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