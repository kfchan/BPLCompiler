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
		int i = 0;
		for (i = 0; i < this.cachedTokens.size(); i++) {
			Token currToken = this.cachedTokens.get(i);
			if (currToken.getPosition() > token.getPosition()) {
				break;
			} else if (currToken.getPosition() == token.getPosition()) {
				return;
			}
		}
		this.cachedTokens.add(i, token);
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

		if (this.hasNextToken()) {
			Token token = this.getNextToken();
			if (token.getType() == Token.T_EOF) {
				declarationList.addChild(declaration);
				return declarationList;
			}
			this.cacheToken();
		}

		BPLNode declarationListChild = this.declarationList();
		declarationList.addChild(declarationListChild);
		declarationList.setLineNumber(declarationListChild.getLineNumber());
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

		BPLNode params = this.params();

		checkForRightParen();

		BPLNode compoundStmt = this.compoundStmt();

		BPLNode funDec = new BPLNode("FUN_DEC", type.getLineNumber());
		funDec.addChild(type);
		funDec.addChild(id);
		funDec.addChild(params);
		funDec.addChild(compoundStmt);
		return funDec;
	}

	/**
	* grammar rule for params node
	*/
	private BPLNode params() throws BPLScannerException, BPLParserException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = getNextToken();
		if (token.getType() == Token.T_VOID) {
			BPLNode v = new BPLNode("void", token.getLineNumber());
			BPLNode params = new BPLNode("PARAMS", v.getLineNumber());
			params.addChild(v);
			return params;
		} else if ((token.getType() != Token.T_INT) && (token.getType() != Token.T_VOID) && (token.getType() != Token.T_STRING)) {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}
		this.cacheToken();
		BPLNode plist = this.paramList();
		BPLNode params = new BPLNode("PARAMS", plist.getLineNumber());
		params.addChild(plist);
		return params;
	}

	/**
	* grammar rule for param list node
	*/
	private BPLNode paramList() throws BPLScannerException, BPLParserException {
		BPLNode param = this.param();
		BPLNode paramList = new BPLNode("PARAM_LIST", param.getLineNumber());

		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();
		if (token.getType() == Token.T_RPAREN) {
			this.cacheToken();
			paramList.addChild(param);
			return paramList;
		} else if (token.getType() != Token.T_COMMA) { // just consume comma if there
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}

		BPLNode childParamList = this.paramList();
		param.addChild(childParamList);
		param.addChild(paramList);

		return param;
	}

	/**
	* grammar rule for param node
	*/	
	private BPLNode param() throws BPLScannerException, BPLParserException {
		BPLNode type = this.typeSpecifier();
		BPLNode param = new BPLNode("PARAM", type.getLineNumber());
		param.addChild(type);

		if (!this.hasNextToken()) {
 			throw new BPLParserException("More tokens expected", type.getLineNumber());
		}

		Token token = this.getNextToken();
		if (token.getType() == Token.T_STAR) { // if next token is star, then there must be an id that follows
			BPLNode star = new BPLNode("*", token.getLineNumber());
			param.addChild(star);

			if (this.hasNextToken()) {
				token = this.getNextToken();
				if (token.getType() != Token.T_ID) {
					throw new BPLParserException("ID missing after '*'", token.getLineNumber());
				}
				BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
				param.addChild(id);
			} else {
				throw new BPLParserException("More tokens expected", token.getLineNumber());
			}
		} else if (token.getType() == Token.T_ID) { // if id token is next, then also check for [] tokens
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			param.addChild(id);

			param = paramBracketHelper(param);
		} else {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}

		return param;		
	}

	/**
	* checks to see if there is a left bracket, then a right bracket
	*/
	private BPLNode paramBracketHelper(BPLNode param) throws BPLScannerException, BPLParserException {
		// check for the left bracket
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected", param.getLineNumber());
		}
		Token token = this.getNextToken();
		if (token.getType() != Token.T_LSQUARE) { // if no left bracket, then cache Token and return
			this.cacheToken();
			return param;
		}
		BPLNode leftBracket = new BPLNode("[", token.getLineNumber());
		param.addChild(leftBracket);

		// check for the right bracket
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected", param.getLineNumber());
		}
		token = this.getNextToken();
		if (token.getType() != Token.T_RSQUARE) {
			throw new BPLParserException("Missing token ']'", param.getLineNumber());
		}
		BPLNode rightBracket = new BPLNode("]", token.getLineNumber());
		param.addChild(rightBracket);

		return param;
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
		this.cacheToken();
		if ((token.getType() != Token.T_INT) && (token.getType() != Token.T_VOID) && (token.getType() != Token.T_STRING)) {
			return new BPLNode("<empty>", token.getLineNumber());
		}

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
		dec.addChild(type);

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
			this.cacheToken();
			return dec;
		}
		BPLNode leftBracket = new BPLNode("[", token.getLineNumber());
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
		BPLNode rightBracket = new BPLNode("]", token.getLineNumber());
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
		this.cacheToken();
		if (token.getType() == Token.T_RCURLY) {
			return new BPLNode("<empty>", token.getLineNumber());
		}

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
		this.cacheToken();
		
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
				this.cacheToken();
				return node;
			}
		} else {
			throw new BPLParserException("More tokens expected");			
		}

		statement = this.statement();
		BPLNode el = new BPLNode("ELSE_STMT", statement.getLineNumber());
		el.addChild(statement);
		node.addChild(el);

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
			this.cacheToken();
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
		this.cacheToken();
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
		// ends with ), ], ;
		Stack<Integer> brackets = new Stack<Integer>();
		ArrayList<Token> tokens = new ArrayList<Token>();

		Token token;
		while(true){
			if (!this.hasNextToken()) {
				throw new BPLParserException("More tokens expected");
			}
			token = this.getNextToken();
			if ((token.getType() == Token.T_EQ) || (token.getType() == Token.T_SEMICOL)) {
				tokens.add(token);
				break;
			} else if ((token.getType() == Token.T_COMMA) && brackets.isEmpty()) {
				tokens.add(token);
				break;
			} else if (token.getType() == Token.T_LPAREN) {
				brackets.push(Token.T_RPAREN);
			} else if (token.getType() == Token.T_LSQUARE) {			
				brackets.push(Token.T_RSQUARE);
			} else if ((token.getType() == Token.T_RPAREN) || (token.getType() == Token.T_RSQUARE)) {
				if (brackets.isEmpty()) {
					tokens.add(token);
					break;
				}
				int compare = brackets.pop();
				if (compare != token.getType()) {
					throw new BPLParserException("Brackets misplaced", token.getLineNumber());
				}
			}
			tokens.add(token);
		}

		Token cacheLater = null;
		for (int i = 0; i < tokens.size(); i++) {	
			cacheThisToken(tokens.get(i));
		}

		Token lastToken = tokens.get(tokens.size()-1);
		BPLNode exp = new BPLNode("EXPRESSION", tokens.get(0).getLineNumber());
		if (lastToken.getType() == Token.T_EQ) {
			exp = this.assignment(exp);
		} else if (brackets.isEmpty()) {
			BPLNode child = this.compExp();
			exp.addChild(child);
		} else {
			throw new BPLParserException("Invalid expression", lastToken.getLineNumber());
		}

		return exp;
	}

	private BPLNode assignment(BPLNode expression) throws BPLParserException, BPLScannerException {
		BPLNode var = this.var();

		Token token;
		if (this.hasNextToken()) {
			token = this.getNextToken();
			if (token.getType() != Token.T_EQ) {
				throw new BPLParserException("Unexpected token type", token.getLineNumber());
			}
		} else {
			throw new BPLParserException("More tokens expected");
		}

		BPLNode childExp = this.expression();
		expression.addChild(var);
		expression.addChild(new BPLNode("=", token.getLineNumber()));
		expression.addChild(childExp);

		return expression;
	}

	/**
	* grammar rule for variable node
	*/
	private BPLNode var() throws BPLScannerException, BPLParserException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();

		if ((token.getType() != Token.T_ID) && (token.getType() != Token.T_STAR)) {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}
		BPLNode var = new BPLNode("VAR", token.getLineNumber());
		if (token.getType() == Token.T_ID) {
			return varHelper(var, token);
		}

		BPLNode star = new BPLNode("*", token.getLineNumber());
		var.addChild(star);
		
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		token = this.getNextToken();
		if (token.getType() != Token.T_ID) {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}	

		BPLVarNode node = new BPLVarNode(token.getValue(), token.getLineNumber());
		var.addChild(node);
		return var;
	}

	/**
	*
	*/
	private BPLNode varHelper(BPLNode var, Token token) throws BPLScannerException, BPLParserException {
		BPLVarNode node = new BPLVarNode(token.getValue(), token.getLineNumber());
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		var.addChild(node);
		Token nextToken = this.getNextToken();
		if (nextToken.getType() != Token.T_LSQUARE) {
			this.cacheToken();
			return var;
		}

		var.addChild(new BPLNode("[", nextToken.getLineNumber()));
		var.addChild(this.expression());
		checkForRightSquare();
		var.addChild(new BPLNode("]", this.currentToken.getLineNumber()));
		return var;
	}

	/**
	* grammar for comp exp
	*/
	private BPLNode compExp() throws BPLParserException, BPLScannerException {
		BPLNode e1 = this.e();
		BPLNode compExp = new BPLNode("COMP_EXP", e1.getLineNumber());

		if (!this.hasNextToken()) {
			compExp.addChild(e1);
			return compExp;
		}

		Token token = this.getNextToken();
		this.cacheToken();
		if (!this.isRelop(token)) { // just E
			compExp.addChild(e1);
			return compExp;
		} 

		BPLNode relop = this.relop();
		
		BPLNode e2 = this.e();
		compExp.addChild(e2);
		compExp.addChild(relop);
		compExp.addChild(e1); // I think this is the right order?
		return compExp;
	}

	/**
	* checks to see if the next token is a relop token
	*/
	private boolean isRelop(Token token) {
		if ((token.getType() == Token.T_LEQ) || (token.getType() == Token.T_LESS) || (token.getType() == Token.T_EQCOMP) ||
			(token.getType() == Token.T_NEQ) || (token.getType() == Token.T_GREAT) || (token.getType() == Token.T_GEQ)) {
			return true;
		}
		return false;
	}

	/**
	* grammar for relop node
	*/
	private BPLNode relop() throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();
		BPLNode relop = new BPLNode("RELOP", token.getLineNumber());
		BPLNode child;

		if (token.getType() == Token.T_LEQ) {
			child = new BPLNode("<=", token.getLineNumber());
		} else if (token.getType() == Token.T_LESS) {
			child = new BPLNode("<", token.getLineNumber());
		} else if (token.getType() == Token.T_EQCOMP) {
			child = new BPLNode("==", token.getLineNumber());
		} else if (token.getType() == Token.T_NEQ) {
			child = new BPLNode("!=", token.getLineNumber());
		} else if (token.getType() == Token.T_GREAT) {
			child = new BPLNode(">", token.getLineNumber());
		} else if (token.getType() == Token.T_GEQ) {
			child = new BPLNode(">=", token.getLineNumber());
		} else {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}
		relop.addChild(child);
		return relop;
	}

	/**
	* grammar rule for E
	*/
	private BPLNode e() throws BPLParserException, BPLScannerException {
		BPLNode t = this.t();
		BPLNode e = new BPLNode("E", t.getLineNumber());

		if (!this.hasNextToken()) {
			e.addChild(t);
			return e;
		}

		Token token = this.getNextToken();
		this.cacheToken();
		if ((token.getType() != Token.T_PLUS) && (token.getType() != Token.T_MINUS)) {
			e.addChild(t);
			return e;			
		}

		BPLNode addop = this.addop();
		BPLNode childE = this.e();
		e.addChild(childE);
		e.addChild(addop);
		e.addChild(t);

		return e;
	}

	/**
	* grammar rule for addop
	*/
	private BPLNode addop() throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();
		BPLNode addop = new BPLNode("ADDOP", token.getLineNumber());
		BPLNode child;

		if (token.getType() == Token.T_PLUS) {
			child = new BPLNode("+", token.getLineNumber());
		} else if (token.getType() == Token.T_MINUS) {
			child = new BPLNode("-", token.getLineNumber());
		} else {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		} 
		addop.addChild(child);
		return addop;
	}

	/**
	* grammar rule for t
	*/
	private BPLNode t() throws BPLParserException, BPLScannerException {
		// get a factor
		BPLNode f = this.f();
		BPLNode t = new BPLNode("T", f.getLineNumber());
		
		if (!this.hasNextToken()) {
			t.addChild(f);
			return t;
		}

		Token token = this.getNextToken();
		this.cacheToken();
		if ((token.getType() != Token.T_STAR) && (token.getType() != Token.T_BACKSLASH) && (token.getType() != Token.T_PERCENT)) {
			t.addChild(f);
			return t;
		}

		BPLNode mulop = this.mulop();
		BPLNode childT = this.t();
		t.addChild(childT);
		t.addChild(mulop);
		t.addChild(f);
		return t;
	}	

	/**
	* grammar rule for MULOP
	*/
	private BPLNode mulop() throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();
		BPLNode mulop = new BPLNode("MULOP", token.getLineNumber());
		BPLNode child;

		if (token.getType() == Token.T_STAR) {
			child = new BPLNode("*", token.getLineNumber());
		} else if (token.getType() == Token.T_BACKSLASH) {
			child = new BPLNode("/", token.getLineNumber());
		} else if (token.getType() == Token.T_PERCENT) {
			child = new BPLNode("%", token.getLineNumber());
		} else {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		} 
		mulop.addChild(child);
		return mulop;
	}

	/**
	* grammar rule for F
	*/
	private BPLNode f() throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();
		BPLNode f = new BPLNode("F", token.getLineNumber());

		if (token.getType() == Token.T_MINUS) {
			f.addChild(new BPLNode("-", token.getLineNumber()));
			f.addChild(this.f());
			return f;
		} else if (token.getType() == Token.T_STAR) {
			f.addChild(new BPLNode("*", token.getLineNumber()));
		} else if (token.getType() == Token.T_AMPER) {
			f.addChild(new BPLNode("&", token.getLineNumber()));
		} else {
			this.cacheToken();
		}

		BPLNode factor = this.factor();
		f.addChild(factor);
		return f;
	}

	/**
	* grammar rule for factor node
	*/
	private BPLNode factor() throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();
		BPLNode factor = new BPLNode("FACTOR", token.getLineNumber());

		BPLNode child;
		if (token.getType() == Token.T_LPAREN) {
			// get expression
			child = this.expression();
			checkForRightParen();
		} else if (token.getType() == Token.T_READ) {
			// make read child node, check for left/right parens
			child = new BPLNode("READ", token.getLineNumber());
			checkForLeftParen();
			checkForRightParen();
		} else if (token.getType() == Token.T_NUM) {
			// make new int child node
			child = new BPLIntegerNode(Integer.parseInt(token.getValue()), token.getLineNumber());
		} else if (token.getType() == Token.T_REALSTRING) {
			// make child node with string as value
			child = new BPLNode("STRING", token.getLineNumber());
			child.addChild(new BPLNode(token.getValue(), token.getLineNumber()));
		} else if (token.getType() == Token.T_ID) {
			return factorIDs(factor, token);
		} else {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}
		factor.addChild(child);

		return factor;
	}

	/**
	* addes child nodes to factor node for anything that starts with an id
	*/
	private BPLNode factorIDs(BPLNode factor, Token idToken) throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();
		if (token.getType() == Token.T_LSQUARE) {
			factor.addChild(new BPLVarNode(idToken.getValue(), idToken.getLineNumber()));
			factor.addChild(new BPLNode("[", token.getLineNumber()));
			factor.addChild(this.expression());
			checkForRightSquare();
			factor.addChild(new BPLNode("]", this.currentToken.getLineNumber()));
		} else if (token.getType() == Token.T_LPAREN) {
			cacheThisToken(idToken);
			cacheThisToken(token);
			factor.addChild(this.funCall());
		} else {
			factor.addChild(new BPLVarNode(idToken.getValue(), idToken.getLineNumber()));
			cacheToken();
		}

		return factor;
	}

	/**
	* grammar rule for function calls
	*/
	private BPLNode funCall() throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}
		Token idToken = this.getNextToken();
		if (idToken.getType() != Token.T_ID) {
			throw new BPLParserException("Unexpected token type", idToken.getLineNumber());
		}
		checkForLeftParen();
		BPLNode args = this.args();
		checkForRightParen();
		BPLNode funCall = new BPLNode("FUN_CALL", idToken.getLineNumber());
		funCall.addChild(new BPLVarNode(idToken.getValue(), idToken.getLineNumber()));
		funCall.addChild(args);
		return funCall;
	}

	/**
	* grammar rule for function calls
	*/
	private BPLNode args() throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();
		cacheToken();
		BPLNode args = new BPLNode("ARGS", token.getLineNumber());
		if (token.getType() == Token.T_RPAREN) {
			args.addChild(new BPLNode("<empty>", token.getLineNumber()));
			return args;
		}

		args.addChild(this.argList());
		return args;
	}

	/**
	* grammar rule for function calls
	*/
	private BPLNode argList() throws BPLParserException, BPLScannerException {
		BPLNode exp = this.expression();
		BPLNode argList = new BPLNode("ARGS", exp.getLineNumber());

		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}

		Token token = this.getNextToken();

		if (token.getType() == Token.T_RPAREN) {
			this.cacheToken();
			argList.addChild(exp);
			return exp;
		} else if (token.getType() != Token.T_COMMA) {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}



		BPLNode childArgList = this.argList();
		argList.addChild(childArgList);
		argList.addChild(exp);
		return argList;
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
	* checks for and consumes a right square bracket
	*/
	private void checkForRightSquare() throws BPLParserException, BPLScannerException {
		if (hasNextToken()) { // check to make sure there is a ) token
			Token token = getNextToken();
			if (token.getType() != Token.T_RSQUARE) {
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