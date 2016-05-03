package Compiler;

import java.util.*;

public class BPLParser {
	private final BPLScanner scanner;

	private Token currentToken;
	private BPLNode head;
	private boolean firstToken;
	private LinkedList<Token> cachedTokens;

	public BPLParser(String fileName) throws BPLException {
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
	public Token getNextToken() throws BPLException {
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
	public void cacheToken() throws BPLException {
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
	private BPLNode program() throws BPLException {
		BPLNode declarationList = this.declarationList();
		BPLNode program = new BPLNode("PROGRAM", declarationList.getLineNumber());
		program.addChild(declarationList);
		return program;
	}

	/**
	* grammar rule for declaration list node
	*/	
	private BPLNode declarationList() throws BPLException {
		BPLNode declaration = this.declaration();
		this.checkForNextToken();
		Token token = this.getNextToken();
		BPLNode declarationList = new BPLNode("DECLARATION_LIST", declaration.getLineNumber());	
		if (token.getType() == Token.T_EOF) {
			declarationList.addChild(declaration);
			return declarationList;
		}
		this.cacheToken();
		BPLNode declarationListChild = this.declarationList();
		declarationList.addChild(declaration);
		declarationList.addChild(declarationListChild);
		return declarationList;
	}

	/**
	* grammar rule for declaration node
	*/
	private BPLNode declaration() throws BPLException {
		this.checkForNextToken();
		Token token1 = this.getNextToken(); // should be a type specifier
		if ((token1.getType() != Token.T_INT) && (token1.getType() != Token.T_VOID) && (token1.getType() != Token.T_STRING)) {
			throw new BPLParserException("Missing token: int, void, or string. Found token: " + token1.getValue(), token1.getLineNumber());
		}

		this.checkForNextToken();
		Token token2 = this.getNextToken();
		this.checkTokenType(token2, Token.T_ID, "id");
		if (token2.getType() == Token.T_STAR) { // var_dec
			cacheThisToken(token1);
			cacheThisToken(token2);
			BPLNode varDec = this.varDec();
			BPLNode dec = new BPLNode("DECLARATION", token2.getLineNumber());
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
			throw new BPLParserException("Missing token: ;, [, or (. Found token: " + token3.getValue(), token3.getLineNumber());
		}

		BPLNode dec = new BPLNode("DECLARATION", token3.getLineNumber());
		dec.addChild(child);
		return dec;
	}

	/**
	* grammar rule for compound statement node
	*/
	private BPLNode funDec() throws BPLException {
		boolean isMain = false;
		BPLNode type = this.typeSpecifier();
		this.checkForNextToken();
		Token token = getNextToken();
		this.checkTokenType(token, Token.T_ID, "id");	

		BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
		this.checkAndConsumeToken(Token.T_LPAREN, "(");
		BPLNode params = this.params();
		this.checkAndConsumeToken(Token.T_RPAREN, ")");
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
	private BPLNode params() throws BPLException {
		this.checkForNextToken();
		Token token = getNextToken();
		if (token.getType() == Token.T_VOID) {
			BPLNode v = new BPLNode("void", token.getLineNumber());
			BPLNode params = new BPLNode("PARAMS", v.getLineNumber());
			params.addChild(v);
			return params;
		} else if ((token.getType() != Token.T_INT) && (token.getType() != Token.T_VOID) && (token.getType() != Token.T_STRING)) {
			throw new BPLParserException("Missing token: int, void, or string. Found token: " + token.getValue(), token.getLineNumber());
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
	private BPLNode paramList() throws BPLException {
		BPLNode param = this.param();
		this.checkForNextToken();
		Token token = this.getNextToken();
		BPLNode paramList = new BPLNode("PARAM_LIST", token.getLineNumber());
		
		if (token.getType() == Token.T_RPAREN) {
			this.cacheToken();
			paramList.addChild(param);
			return paramList;
		} 
		this.checkTokenType(token, Token.T_COMMA, ",");
		BPLNode childParamList = this.paramList();
		paramList.addChild(param);
		paramList.addChild(childParamList);
		return paramList;
	}

	/**
	* grammar rule for param node
	*/	
	private BPLNode param() throws BPLException {
		BPLNode type = this.typeSpecifier();
		this.checkForNextToken();
		Token token = this.getNextToken();
		BPLNode param = new BPLNode("PARAM", token.getLineNumber());
		param.addChild(type);
		if (token.getType() == Token.T_STAR) { // if next token is star, then there must be an id that follows
			BPLNode star = new BPLNode("*", token.getLineNumber());
			param.addChild(star);
			this.checkForNextToken();
			token = this.getNextToken();
			this.checkTokenType(token, Token.T_ID, "id");
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			param.addChild(id);
		} else if (token.getType() == Token.T_ID) { // if id token is next, then also check for [] tokens
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			param.addChild(id);
			param = paramBracketHelper(param);
		} else {
			throw new BPLParserException("Missing token * or id. Found token: "  + token.getValue(), token.getLineNumber());
		}
		return param;		
	}

	/**
	* checks to see if there is a left bracket, then a right bracket
	*/
	private BPLNode paramBracketHelper(BPLNode param) throws BPLException {
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
		this.checkTokenType(token, Token.T_RSQUARE, "]"); // check for the right bracket
		BPLNode rightBracket = new BPLNode("]", token.getLineNumber());
		param.addChild(rightBracket);
		return param;
	}

	/**
	* grammar rule for compound statement node
	*/
	private BPLNode compoundStmt() throws BPLException {
		this.checkForNextToken();
		Token lcurly = this.getNextToken();
		this.checkTokenType(lcurly, Token.T_LCURLY, "{");

		BPLNode localDs  = this.localDecs();
		BPLNode statementList = this.statementList();

		this.checkForNextToken();
		Token token = this.getNextToken();
		this.checkTokenType(token, Token.T_RCURLY, "}");

		BPLNode compoundStmt = new BPLNode("COMPOUND_STMT", lcurly.getLineNumber());
		compoundStmt.addChild(localDs);
		compoundStmt.addChild(statementList);
		return compoundStmt;
	}

	/**
	* grammar for local declarations
	**/
	private BPLNode localDecs() throws BPLException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.cacheToken();
		if ((token.getType() != Token.T_INT) && (token.getType() != Token.T_VOID) && (token.getType() != Token.T_STRING)) {
			return new BPLNode("<empty>", token.getLineNumber());
		}

		BPLNode varD = this.varDec();
		BPLNode localD = new BPLNode("LOCAL_DECS", varD.getLineNumber());
		BPLNode localDs = this.localDecs();
		
		localD.addChild(varD);
		localD.addChild(localDs);
		return localD;
	} 

	/**
	* grammar rule for variable declaration
	*/
	private BPLNode varDec() throws BPLException {
		BPLNode type = this.typeSpecifier();
		this.checkForNextToken();
		Token token = this.getNextToken();
		BPLNode dec = new BPLNode("VAR_DEC", token.getLineNumber());
		dec.addChild(type);
		if (token.getType() == Token.T_STAR) { // if next token is star, then there must be an id that follows
			BPLNode star = new BPLNode("*", token.getLineNumber());
			dec.addChild(star);
			this.checkForNextToken();
			token = this.getNextToken();
			this.checkTokenType(token, Token.T_ID, "id");
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			dec.addChild(id);
		} else if (token.getType() == Token.T_ID) { // if id token is next, then also check for [] tokens
			BPLNode id = new BPLVarNode(token.getValue(), token.getLineNumber());
			dec.addChild(id);

			dec = varDecBracketHelper(dec);
		} else {
			throw new BPLParserException("Missing token: * or id   Found token: " + token.getValue(), token.getLineNumber());
		}
		this.checkAndConsumeToken(Token.T_SEMICOL, ";");
		return dec;
	}

	/**
	* checks to see if there is a left bracket
	* if there isn't, return
	* if there is, check for int and right bracket
	*/
	private BPLNode varDecBracketHelper(BPLNode dec) throws BPLException {
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
		this.checkTokenType(token, Token.T_NUM, "integer"); // check for integer between the brackets
		int val = Integer.parseInt(token.getValue());
		BPLNode integer = new BPLIntegerNode(val, token.getLineNumber());
		dec.addChild(integer);

		this.checkForNextToken();
		token = this.getNextToken();
		this.checkTokenType(token, Token.T_RSQUARE, "]"); // check for the right bracket
		BPLNode rightBracket = new BPLNode("]", token.getLineNumber());
		dec.addChild(rightBracket);
		return dec;
	}

	/**
	* grammar rule for the type specifier node
	*/
	private BPLNode typeSpecifier() throws BPLException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_INT) {
			return new BPLNode("int", token.getLineNumber());
		} else if (token.getType() == Token.T_VOID) {
			return new BPLNode("void", token.getLineNumber());
		} else if (token.getType() == Token.T_STRING) {
			return new BPLNode("string", token.getLineNumber());
		} 
		throw new BPLParserException("Missing token: int, void, or string. Found token: " + token.getValue(), token.getLineNumber());
	}

	/**
	* grammar rule for statementList
	*/
	private BPLNode statementList() throws BPLException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.cacheToken();
		if (token.getType() == Token.T_RCURLY) {
			return new BPLNode("<empty>", token.getLineNumber());
		}
		BPLNode statement = this.statement();
		BPLNode statementList = new BPLNode("STATEMENT_LIST", token.getLineNumber());
		BPLNode sList = this.statementList();

		statementList.addChild(statement);
		statementList.addChild(sList);
		return statementList;
	}

	/**
	* grammar rule for statement node
	*/
	private BPLNode statement() throws BPLException {
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
		BPLNode statement = new BPLNode("STATEMENT", token.getLineNumber());
		statement.addChild(node);
		return statement;
	}

	/**
	* grammar rule for the while statement
	*/
	private BPLNode ifStmt() throws BPLException {
		this.checkForNextToken();
		Token ifTok = this.getNextToken();
		this.checkTokenType(ifTok, Token.T_IF, "if");

		BPLNode node = new BPLNode("IF_STMT", ifTok.getLineNumber());
		this.checkAndConsumeToken(Token.T_LPAREN, "(");
		BPLNode expres = this.expression();
		node.addChild(expres);
		this.checkAndConsumeToken(Token.T_RPAREN, ")");
		BPLNode statement = this.statement();
		node.addChild(statement);

		this.checkForNextToken();
		Token token = getNextToken();
		if (token.getType() != Token.T_ELSE) {
			this.cacheToken();
			return node;
		}
		statement = this.statement();
		node.addChild(statement);
		return node;
	}	

	/**
	* grammar rule for the while statement
	*/
	private BPLNode whileStmt() throws BPLException {
		this.checkForNextToken();
		Token whileTok = this.getNextToken();
		this.checkTokenType(whileTok, Token.T_WHILE, "while");

		BPLNode node = new BPLNode("WHILE_STMT", whileTok.getLineNumber());
		this.checkAndConsumeToken(Token.T_LPAREN, "(");
		BPLNode expres = this.expression();
		node.addChild(expres);
		this.checkAndConsumeToken(Token.T_RPAREN, ")");
		BPLNode statement = this.statement();
		node.addChild(statement);
		return node;
	}

	/**
	* grammar rule for the write statements
	*/
	private BPLNode writeStmt() throws BPLException {
		this.checkForNextToken();
		Token writeTok = this.getNextToken();
		if ((writeTok.getType() != Token.T_WRITE) && (writeTok.getType() != Token.T_WRITELN)) {
			throw new BPLParserException("Missing token: write or writeln. Found token: " + writeTok.getValue(), writeTok.getLineNumber());
		}

		BPLNode node = new BPLNode("WRITE_STMT", writeTok.getLineNumber());
		this.checkAndConsumeToken(Token.T_LPAREN, "(");
		if (writeTok.getType() == Token.T_WRITE) {
			BPLNode expres = this.expression();
			node.addChild(expres);
		}
		this.checkAndConsumeToken(Token.T_RPAREN, ")");
		this.checkAndConsumeToken(Token.T_SEMICOL, ";");
		return node;
	}

	/**
	* grammar rule for the return statement
	*/
	private BPLNode returnStmt() throws BPLException {
		this.checkForNextToken();
		Token returnTok = this.getNextToken();
		this.checkTokenType(returnTok, Token.T_RETURN, "return");

		this.checkForNextToken();
		Token token = getNextToken();
		BPLNode returnStmt = new BPLNode("RETURN_STMT", returnTok.getLineNumber());	
		if (token.getType() != Token.T_SEMICOL) { // if no semicolin, then there must be an expression
			this.cacheToken();
			BPLNode expression = this.expression();
			returnStmt.setLineNumber(token.getLineNumber());				
			returnStmt.addChild(expression);
		} else {
			return returnStmt;
		}
		this.checkAndConsumeToken(Token.T_SEMICOL, ";");
		return returnStmt;
	}

	/**
	* grammar rule for expression_stmt node
	*/
	private BPLNode expressionStmt() throws BPLException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_SEMICOL) {
			return new BPLNode("EXPRESSION_STMT", token.getLineNumber());
		}
		this.cacheToken();
		BPLNode expression = this.expression();
		BPLNode expressionStmt = new BPLNode("EXPRESSION_STMT", expression.getLineNumber());
		
		expressionStmt.addChild(expression);
		this.checkAndConsumeToken(Token.T_SEMICOL, ";");
		return expressionStmt;
	}

	/**
	* grammar rule for expression node
	*/
	private BPLNode expression() throws BPLException {
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
					throw new BPLParserException("Brackets misplaced: " + token.getValue(), token.getLineNumber());
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
			throw new BPLParserException("Invalid expression token: " + lastToken.getValue(), lastToken.getLineNumber());
		}
		return exp;
	}

	/**
	* does the assignment part of the expression grammar rule
	*/
	private BPLNode assignment(BPLNode expression) throws BPLException {
		BPLNode var = this.var();
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.checkTokenType(token, Token.T_EQ, "=");

		BPLNode childExp = this.expression();
		expression.addChild(var);
		expression.addChild(new BPLNode("=", token.getLineNumber()));
		expression.addChild(childExp);

		return expression;
	}

	/**
	* grammar rule for variable node
	*/
	private BPLNode var() throws BPLException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		if ((token.getType() != Token.T_ID) && (token.getType() != Token.T_STAR)) {
			throw new BPLParserException("Missing token: * or id. Found token: " + token.getValue(), token.getLineNumber());
		}

		BPLNode var = new BPLNode("VAR", token.getLineNumber());
		if (token.getType() == Token.T_ID) {
			return varHelper(var, token);
		}
		BPLNode star = new BPLNode("*", token.getLineNumber());
		var.addChild(star);
		this.checkForNextToken();
		token = this.getNextToken();
		this.checkTokenType(token, Token.T_ID, "id");
		BPLVarNode node = new BPLVarNode(token.getValue(), token.getLineNumber());
		var.addChild(node);
		return var;
	}

	private BPLNode varHelper(BPLNode var, Token token) throws BPLException {
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
		this.checkAndConsumeToken(Token.T_RSQUARE, "[");
		var.addChild(new BPLNode("]", this.currentToken.getLineNumber()));
		return var;
	}

	/**
	* grammar for comp exp
	*/
	private BPLNode compExp() throws BPLException {
		BPLNode e1 = this.e();
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.cacheToken();
		BPLNode compExp = new BPLNode("COMP_EXP", token.getLineNumber());		
		if (!this.isRelop(token)) { // just E
			compExp.addChild(e1);
			return compExp;
		} 
		BPLNode relop = this.relop();
		BPLNode e2 = this.e();
		compExp.addChild(e1);
		compExp.addChild(relop);
		compExp.addChild(e2);
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
	private BPLNode relop() throws BPLException {
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
			throw new BPLParserException("Missing comparator token. Found token: " + token.getValue(), token.getLineNumber());
		}
		relop.addChild(child);
		return relop;
	}

	/**
	* grammar rule for E
	*/
	private BPLNode e() throws BPLException {
		BPLNode t = this.t();
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.cacheToken();
		if ((token.getType() != Token.T_PLUS) && (token.getType() != Token.T_MINUS)) {
			return t;
		}

		BPLNode e = new BPLNode("E", token.getLineNumber());
		e.addChild(t);
		while ((token.getType() == Token.T_PLUS) || (token.getType() == Token.T_MINUS)) {
			BPLNode addop = this.addop();
			BPLNode newT = this.t();
			BPLNode newParent = new BPLNode("E", token.getLineNumber());
			newParent.addChild(e);
			newParent.addChild(addop);
			newParent.addChild(newT);
			e = newParent;
			this.checkForNextToken();
			token = this.getNextToken();
			this.cacheToken();
		}
		return e;
	}

	/**
	* grammar rule for addop
	*/
	private BPLNode addop() throws BPLException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		BPLNode addop = new BPLNode("ADDOP", token.getLineNumber());

		BPLNode child;
		if (token.getType() == Token.T_PLUS) {
			child = new BPLNode("+", token.getLineNumber());
		} else if (token.getType() == Token.T_MINUS) {
			child = new BPLNode("-", token.getLineNumber());
		} else {
			throw new BPLParserException("Missing token: + or -. Found token: " + token.getValue(), token.getLineNumber());
		} 

		addop.addChild(child);
		return addop;
	}

	/**
	* grammar rule for t
	*/
	private BPLNode t() throws BPLException {
		BPLNode f = this.f();
		this.checkForNextToken();
		Token token = this.getNextToken();
		this.cacheToken();
		if ((token.getType() != Token.T_STAR) && (token.getType() != Token.T_BACKSLASH) && (token.getType() != Token.T_PERCENT)) {
			return f;
		}

		BPLNode t = new BPLNode("T", token.getLineNumber());
		t.addChild(f);
		while ((token.getType() == Token.T_STAR) || (token.getType() == Token.T_BACKSLASH) || (token.getType() == Token.T_PERCENT)) {
			BPLNode mulop = this.mulop();
			BPLNode newF = this.f();
			BPLNode newParent = new BPLNode("T", token.getLineNumber());
			newParent.addChild(t);
			newParent.addChild(mulop);
			newParent.addChild(newF);
			t = newParent;
			this.checkForNextToken();
			token = this.getNextToken();
			this.cacheToken();
		}
		return t;
	}	

	/**
	* grammar rule for MULOP
	*/
	private BPLNode mulop() throws BPLException {
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
			throw new BPLParserException("Missing token: *, /, or %. Found token: " + token.getValue(), token.getLineNumber());
		}
		mulop.addChild(child);
		return mulop;
	}

	/**
	* grammar rule for F
	*/
	private BPLNode f() throws BPLException {
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
	private BPLNode factor() throws BPLException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		BPLNode factor = new BPLNode("FACTOR", token.getLineNumber());

		BPLNode child;
		if (token.getType() == Token.T_LPAREN) { // get expression
			child = this.expression();
			this.checkAndConsumeToken(Token.T_RPAREN, "[");
		} else if (token.getType() == Token.T_READ) { // make read child node, check for left/right parens
			child = new BPLNode("READ", token.getLineNumber());
			this.checkAndConsumeToken(Token.T_LPAREN, "(");
			this.checkAndConsumeToken(Token.T_RPAREN, ")");
		} else if (token.getType() == Token.T_NUM) { // make new int child node
			child = new BPLIntegerNode(Integer.parseInt(token.getValue()), token.getLineNumber());
		} else if (token.getType() == Token.T_REALSTRING) { // make child node with string as value
			child = new BPLNode("STRING", token.getLineNumber());
			child.addChild(new BPLNode(token.getValue(), token.getLineNumber()));
		} else if (token.getType() == Token.T_ID) {
			return factorIDs(factor, token);
		} else {
			throw new BPLParserException("Missing token: (, read, integer, string literal, or id. Found token: " + token.getValue(), token.getLineNumber());
		}
		factor.addChild(child);
		return factor;
	}

	/**
	* addes child nodes to factor node for anything that starts with an id
	*/
	private BPLNode factorIDs(BPLNode factor, Token idToken) throws BPLException {
		this.checkForNextToken();
		Token token = this.getNextToken();
		if (token.getType() == Token.T_LSQUARE) {
			factor.addChild(new BPLVarNode(idToken.getValue(), idToken.getLineNumber()));
			factor.addChild(new BPLNode("[", token.getLineNumber()));
			factor.addChild(this.expression());
			this.checkAndConsumeToken(Token.T_RSQUARE, "]");
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
	private BPLNode funCall() throws BPLException {
		this.checkForNextToken();
		Token idToken = this.getNextToken();
		this.checkTokenType(idToken, Token.T_ID, "id");
		this.checkAndConsumeToken(Token.T_LPAREN, "(");
		BPLNode args = this.args();
		this.checkAndConsumeToken(Token.T_RPAREN, ")");
		BPLNode funCall = new BPLNode("FUN_CALL", idToken.getLineNumber());
		funCall.addChild(new BPLVarNode(idToken.getValue(), idToken.getLineNumber()));
		funCall.addChild(args);
		return funCall;
	}

	/**
	* grammar rule for function calls
	*/
	private BPLNode args() throws BPLException {
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
	private BPLNode argList() throws BPLException {
		BPLNode exp = this.expression();
		this.checkForNextToken();
		Token token = this.getNextToken();
		BPLNode argList = new BPLNode("ARG_LIST", token.getLineNumber());
		if (token.getType() == Token.T_RPAREN) {
			this.cacheToken();
			argList.addChild(exp);
			return argList;
		}
		this.checkTokenType(token, Token.T_COMMA, ",");
		BPLNode childArgList = this.argList();
		argList.addChild(exp);
		argList.addChild(childArgList);
		return argList;
	}	

	/**
	* checks to see if there is a next token and throws exception if not
	*/
	private void checkForNextToken() throws BPLException {
		if (!this.hasNextToken()) {
			throw new BPLParserException("Unexpected end of file");
		}
	}

	/**
	* checks the token type
	*/
	private void checkTokenType(Token token, int type, String typeString) throws BPLException {
		if (token.getType() != type) {
			throw new BPLParserException("Missing token: " + typeString + ". Found token: " + token.getValue(), token.getLineNumber());
		}
	}

	/**
	* checks and consumes the next token
	*/
	private void checkAndConsumeToken(int type, String typeString) throws BPLException {
		this.checkForNextToken();
		this.checkTokenType(this.getNextToken(), type, typeString);
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

	public static void main(String[] pirateArgs) throws BPLException {
		if (pirateArgs.length == 0) {
			System.err.println("File to parse needed!");
			System.exit(1);
		}
		String fileName = pirateArgs[0];
		BPLParser parser = new BPLParser("../" + fileName);
		System.out.println(parser);
		System.exit(0);
	}
}