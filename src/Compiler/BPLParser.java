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

		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_EOF) {
			declarationList.addChild(declaration);
			return declarationList;
		}
		this.cacheToken();
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
		this.checkForNextToken();
		Token token1 = this.getNextToken(); // should be a type specifier
		if ((token1.getType() != Token.T_INT) && (token1.getType() != Token.T_VOID) && (token1.getType() != Token.T_STRING)) {
			throw new BPLParserException("Unexpected token type", token1.getLineNumber());
		}

		this.checkForNextToken();
		Token token2 = this.getNextToken();
		this.checkTokenType(token2, Token.T_ID);
		if (token2.getType() == Token.T_STAR) { // var_dec
			cacheThisToken(token1);
			cacheThisToken(token2);
			BPLNode varDec = this.varDec();
			BPLNode dec = new BPLNode("DECLARATION", token1.getLineNumber());
			dec.addChild(varDec);
			return dec;
		} 
		
		this.checkForNextToken();
		BPLNode child;
		Token token3 = this.getNextToken();
		if ((token3.getType() == Token.T_SEMICOL) || (token3.getType() == Token.T_LSQUARE)) { // var_dec
			cacheThisToken(token1);
			cacheThisToken(token2);
			cacheThisToken(token3);	
			child = this.varDec();		
		} else if (token3.getType() == Token.T_LPAREN) { // fun_dec
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
		this.checkForNextToken();
		Token token = getNextToken();
		this.checkTokenType(token, Token.T_ID);	

		BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
		this.checkAndConsumeToken(Token.T_LPAREN);
		BPLNode params = this.params();
		this.checkAndConsumeToken(Token.T_RPAREN);
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
		this.checkForNextToken();
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
		this.checkForNextToken();
		Token token = this.getNextToken();
		
		if (token.getType() == Token.T_RPAREN) {
			this.cacheToken();
			paramList.addChild(param);
			return paramList;
		} 
		this.checkTokenType(token, Token.T_COMMA);
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

		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_STAR) { // if next token is star, then there must be an id that follows
			BPLNode star = new BPLNode("*", token.getLineNumber());
			param.addChild(star);
			this.checkForNextToken();
			token = this.getNextToken();
			this.checkTokenType(token, Token.T_ID);
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			param.addChild(id);
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
		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() != Token.T_LSQUARE) { // if no left bracket, then cache Token and return
			this.cacheToken();
			return param;
		}
		BPLNode leftBracket = new BPLNode("[", token.getLineNumber());
		param.addChild(leftBracket);

		this.checkForNextToken();
		token = this.getNextToken();
		this.checkTokenType(token, Token.T_RSQUARE); // check for the right bracket
		BPLNode rightBracket = new BPLNode("]", token.getLineNumber());
		param.addChild(rightBracket);
		return param;
	}

	/**
	* grammar rule for compound statement node
	*/
	private BPLNode compoundStmt() throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
		Token lcurly = this.getNextToken();
		this.checkTokenType(lcurly, Token.T_LCURLY);

		BPLNode localDs  = this.localDecs();
		BPLNode statementList = this.statementList();

		if (this.hasNextToken()) {
			Token token = this.getNextToken();
			this.checkTokenType(token, Token.T_RCURLY);
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
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.cacheToken();
		if ((token.getType() != Token.T_INT) && (token.getType() != Token.T_VOID) && (token.getType() != Token.T_STRING)) {
			return new BPLNode("<empty>", token.getLineNumber());
		}

		BPLNode varD = this.varDec();
		BPLNode localD = new BPLNode("LOCAL_DECS", varD.getLineNumber());
		BPLNode localDs = this.localDecs();
		
		localD.addChild(localDs);
		localD.addChild(varD);
		return localD;
	} 

	/**
	* grammar rule for variable declaration
	*/
	private BPLNode varDec() throws BPLScannerException, BPLParserException {
		BPLNode type = this.typeSpecifier();
		BPLNode dec = new BPLNode("VAR_DEC", type.getLineNumber());
		dec.addChild(type);

		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_STAR) { // if next token is star, then there must be an id that follows
			BPLNode star = new BPLNode("*", token.getLineNumber());
			dec.addChild(star);
			this.checkForNextToken();
			token = this.getNextToken();
			this.checkTokenType(token, Token.T_ID);
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			dec.addChild(id);
		} else if (token.getType() == Token.T_ID) { // if id token is next, then also check for [] tokens
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			dec.addChild(id);

			dec = varDecBracketHelper(dec);
		} else {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}

		this.checkAndConsumeToken(Token.T_SEMICOL);
		return dec;
	}

	/**
	* checks to see if there is a left bracket
	* if there isn't, return
	* if there is, check for int and right bracket
	*/
	private BPLNode varDecBracketHelper(BPLNode dec) throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() != Token.T_LSQUARE) { // if no left bracket, then cache Token and return
			this.cacheToken();
			return dec;
		}
		BPLNode leftBracket = new BPLNode("[", token.getLineNumber());
		dec.addChild(leftBracket);

		this.checkForNextToken();
		token = this.getNextToken();
		this.checkTokenType(token, Token.T_NUM); // check for integer between the brackets
		int val = Integer.parseInt(token.getValue());
		BPLNode integer = new BPLIntegerNode(val, token.getLineNumber());
		dec.addChild(integer);

		this.checkForNextToken();
		token = this.getNextToken();
		this.checkTokenType(token, Token.T_RSQUARE); // check for the right bracket
		BPLNode rightBracket = new BPLNode("]", token.getLineNumber());
		dec.addChild(rightBracket);
		return dec;
	}

	/**
	* grammar rule for the type specifier node
	*/
	private BPLNode typeSpecifier() throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_INT) {
			return new BPLNode("int", token.getLineNumber());
		} else if (token.getType() == Token.T_VOID) {
			return new BPLNode("void", token.getLineNumber());
		} else if (token.getType() == Token.T_STRING) {
			return new BPLNode("string", token.getLineNumber());
		} 
		throw new BPLParserException("Unexpected token type", token.getLineNumber());
	}

	/**
	* grammar rule for statementList
	*/
	private BPLNode statementList() throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.cacheToken();
		if (token.getType() == Token.T_RCURLY) {
			return new BPLNode("<empty>", token.getLineNumber());
		}

		BPLNode statement = this.statement();
		BPLNode statementList = new BPLNode("STATEMENT_LIST", statement.getLineNumber());
		BPLNode sList = this.statementList();

		statementList.addChild(sList);
		statementList.addChild(statement);
		return statementList;
	}

	/**
	* grammar rule for statement node
	*/
	private BPLNode statement() throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
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
		this.checkForNextToken();
		Token ifTok = this.getNextToken();
		this.checkTokenType(ifTok, Token.T_IF);

		BPLNode node = new BPLNode("IF_STMT", ifTok.getLineNumber());
		this.checkAndConsumeToken(Token.T_LPAREN);
		BPLNode expres = this.expression();
		node.addChild(expres);
		this.checkAndConsumeToken(Token.T_RPAREN);
		BPLNode statement = this.statement();
		node.addChild(statement);

		this.checkForNextToken();
		Token token = getNextToken();
		if (token.getType() != Token.T_ELSE) {
			this.cacheToken();
			return node;
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
		this.checkForNextToken();
		Token whileTok = this.getNextToken();
		this.checkTokenType(whileTok, Token.T_WHILE);

		BPLNode node = new BPLNode("WHILE_STMT", whileTok.getLineNumber());
		this.checkAndConsumeToken(Token.T_LPAREN);
		BPLNode expres = this.expression();
		node.addChild(expres);
		this.checkAndConsumeToken(Token.T_RPAREN);
		BPLNode statement = this.statement();
		node.addChild(statement);
		return node;
	}

	/**
	* grammar rule for the write statements
	*/
	private BPLNode writeStmt() throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
		Token writeTok = this.getNextToken();
		this.checkTokenType(writeTok, Token.T_WRITE);
		this.checkTokenType(writeTok, Token.T_WRITELN);

		BPLNode node = new BPLNode("WRITE_STMT", writeTok.getLineNumber());
		this.checkAndConsumeToken(Token.T_LPAREN);

		if (writeTok.getType() == Token.T_WRITE) {
			BPLNode expres = this.expression();
			node.addChild(expres);
		}

		this.checkAndConsumeToken(Token.T_RPAREN);
		this.checkAndConsumeToken(Token.T_SEMICOL);
		return node;
	}

	/**
	* grammar rule for the return statement
	*/
	private BPLNode returnStmt() throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
		Token returnTok = this.getNextToken();
		this.checkTokenType(returnTok, Token.T_RETURN);

		this.checkForNextToken();
		Token token = getNextToken();
		BPLNode returnStmt = new BPLNode("RETURN_STMT", returnTok.getLineNumber());	
		if (token.getType() != Token.T_SEMICOL) { // if no semicolin, then there must be an expression
			this.cacheToken();
			BPLNode expression = this.expression();
			returnStmt.setLineNumber(expression.getLineNumber());				
			returnStmt.addChild(expression);
		} else {
			return returnStmt;
		}

		this.checkAndConsumeToken(Token.T_SEMICOL);
		return returnStmt;
	}

	/**
	* grammar rule for expression_stmt node
	*/
	private BPLNode expressionStmt() throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_SEMICOL) {
			return new BPLNode("EXPRESSION_STMT", token.getLineNumber());
		}
		this.cacheToken();
		BPLNode expression = this.expression();
		BPLNode expressionStmt = new BPLNode("EXPRESSION_STMT", expression.getLineNumber());
		
		expressionStmt.addChild(expression);
		this.checkAndConsumeToken(Token.T_SEMICOL);
		return expressionStmt;
	}

	/**
	* grammar rule for expression node
	*/
	private BPLNode expression() throws BPLScannerException, BPLParserException {
		Stack<Integer> brackets = new Stack<Integer>();
		ArrayList<Token> tokens = new ArrayList<Token>();

		Token token;
		while(true){
			this.checkForNextToken();
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

		for (int i = 0; i < tokens.size(); i++) { // cache tokens back
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

	/**
	* does the assignment part of the expression grammar rule
	*/
	private BPLNode assignment(BPLNode expression) throws BPLParserException, BPLScannerException {
		BPLNode var = this.var();
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.checkTokenType(token, Token.T_EQ);

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
		this.checkForNextToken();
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
		this.checkForNextToken();
		token = this.getNextToken();
		this.checkTokenType(token, Token.T_ID);
		BPLVarNode node = new BPLVarNode(token.getValue(), token.getLineNumber());
		var.addChild(node);
		return var;
	}

	private BPLNode varHelper(BPLNode var, Token token) throws BPLScannerException, BPLParserException {
		this.checkForNextToken();
		BPLVarNode node = new BPLVarNode(token.getValue(), token.getLineNumber());
		var.addChild(node);

		Token nextToken = this.getNextToken();
		if (nextToken.getType() != Token.T_LSQUARE) {
			this.cacheToken();
			return var;
		}

		var.addChild(new BPLNode("[", nextToken.getLineNumber()));
		var.addChild(this.expression());
		this.checkAndConsumeToken(Token.T_RSQUARE);
		var.addChild(new BPLNode("]", this.currentToken.getLineNumber()));
		return var;
	}

	/**
	* grammar for comp exp
	*/
	private BPLNode compExp() throws BPLParserException, BPLScannerException {
		BPLNode e1 = this.e();
		BPLNode compExp = new BPLNode("COMP_EXP", e1.getLineNumber());
		this.checkForNextToken();

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
		compExp.addChild(e1);
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
		this.checkForNextToken();
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

		this.checkForNextToken();
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
		this.checkForNextToken();
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
		BPLNode f = this.f();
		BPLNode t = new BPLNode("T", f.getLineNumber());

		this.checkForNextToken();
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
		this.checkForNextToken();
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
		this.checkForNextToken();
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
		this.checkForNextToken();
		Token token = this.getNextToken();
		BPLNode factor = new BPLNode("FACTOR", token.getLineNumber());

		BPLNode child;
		if (token.getType() == Token.T_LPAREN) { // get expression
			child = this.expression();
			this.checkAndConsumeToken(Token.T_RPAREN);
		} else if (token.getType() == Token.T_READ) { // make read child node, check for left/right parens
			child = new BPLNode("READ", token.getLineNumber());
			this.checkAndConsumeToken(Token.T_LPAREN);
			this.checkAndConsumeToken(Token.T_RPAREN);
		} else if (token.getType() == Token.T_NUM) { // make new int child node
			child = new BPLIntegerNode(Integer.parseInt(token.getValue()), token.getLineNumber());
		} else if (token.getType() == Token.T_REALSTRING) { // make child node with string as value
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
		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_LSQUARE) {
			factor.addChild(new BPLVarNode(idToken.getValue(), idToken.getLineNumber()));
			factor.addChild(new BPLNode("[", token.getLineNumber()));
			factor.addChild(this.expression());
			this.checkAndConsumeToken(Token.T_RSQUARE);
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
		this.checkForNextToken();
		Token idToken = this.getNextToken();
		this.checkTokenType(idToken, Token.T_ID);
		this.checkAndConsumeToken(Token.T_LPAREN);
		BPLNode args = this.args();
		this.checkAndConsumeToken(Token.T_RPAREN);
		BPLNode funCall = new BPLNode("FUN_CALL", idToken.getLineNumber());
		funCall.addChild(new BPLVarNode(idToken.getValue(), idToken.getLineNumber()));
		funCall.addChild(args);
		return funCall;
	}

	/**
	* grammar rule for function calls
	*/
	private BPLNode args() throws BPLParserException, BPLScannerException {
		this.checkForNextToken();
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
		this.checkForNextToken();
		Token token = this.getNextToken();

		if (token.getType() == Token.T_RPAREN) {
			this.cacheToken();
			argList.addChild(exp);
			return exp;
		}
		this.checkTokenType(token, Token.T_COMMA);
		BPLNode childArgList = this.argList();
		argList.addChild(childArgList);
		argList.addChild(exp);
		return argList;
	}	

	/**
	* checks to see if there is a next token and throws exception if not
	*/
	private void checkForNextToken() throws BPLParserException, BPLScannerException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("More tokens expected");
		}
	}

	/**
	* checks the token type
	*/
	private void checkTokenType(Token token, int type) throws BPLParserException {
		if (token.getType() != type) {
			throw new BPLParserException("Unexpected token type", token.getLineNumber());
		}
	}

	/**
	* checks and consumes the next token
	*/
	private void checkAndConsumeToken(int type) throws BPLParserException, BPLScannerException {
		this.checkForNextToken();
		this.checkTokenType(this.getNextToken(), type);
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