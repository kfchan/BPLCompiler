package Compiler;

import java.util.*;

public class BPLTypeChecker {
	private static final boolean DEBUG = true;
	private static final String TYPE_VAR = "var";
	private static final String TYPE_VOID = "void";
	private static final String TYPE_INT = "int";
	private static final String TYPE_STRING = "string";

	private final BPLParser parser;
	
	private BPLNode parseTree;
	private HashMap<String, BPLNode> globalDecs;
	private LinkedList<BPLNode> localDecs; 
	private Stack<Integer> scopeSizes;

	public BPLTypeChecker(String filename) throws BPLException {
		this.parser = new BPLParser(filename);
		this.parseTree = this.parser.getBPLHead();
		this.globalDecs = new HashMap<String, BPLNode>();
		this.localDecs = new LinkedList<BPLNode>();
		this.scopeSizes = new Stack<Integer>();
		this.typeCheck(this.parseTree);
	}

	private void typeCheck(BPLNode head) throws BPLException {
		BPLNode decList = this.getDecList(head);
		this.findRefHelper(decList);
	}

	private void findRefHelper(BPLNode decList) throws BPLException {
		BPLNode dec = decList.getChild(0);
		BPLNode decChild = dec.getChild(0);
		
		if (decChild.isType("VAR_DEC")) {
			this.addToGlobalDecs(decChild);
		} else {
			this.addFunToDecs(decChild);
			this.handleFunDec(decChild);
		}
		
		if (decList.isChildrenSize(2)) {
			this.findRefHelper(decList.getChild(1));
		}
	}

	private BPLNode getDecList(BPLNode head) throws BPLException {
		if (head.isChildrenSize(0)) {
			this.printDebug("Nothing in BPL file.");
			System.exit(0);
		}
		return head.getChild(0);
	}

	private void addToGlobalDecs(BPLNode decChild) throws BPLException {
		String varName = this.getNameFromVarDec(decChild);
		this.checkVoid(decChild, varName);
		this.printDebug("Adding VAR_DEC " + varName + " to global decs");
		this.globalDecs.put(varName, decChild);
	}

	private void addFunToDecs(BPLNode funDec) {
		BPLNode idNode = funDec.getChild(1);
		String id = ((BPLVarNode) idNode).getID();
		this.printDebug("Adding FUN_DEC " + id + " to global decs");
		this.globalDecs.put(id, funDec);
	}

	private void handleFunDec(BPLNode funDec) throws BPLException {
		// create new local decs
		this.localDecs.clear();

		// params
		this.addParamsToLocal(funDec.getChild(2));

		// compound statement
		this.handleCmpdStmt(funDec.getChild(3));

		// clear localdecs (remove params)
		// this.localDecs.clear();
		// TODO: use while loop when still printing removal and addition of localdecs
		while (!this.localDecs.isEmpty()) {
			BPLNode polled = this.localDecs.poll();
			String polledName = polled.getName();
			this.printDebug("Removing PARAM " + polledName + " from local decs");
		}
	}

	private void addParamsToLocal(BPLNode params) throws BPLException {
		BPLNode paramsChild = params.getChild(0);
		if (paramsChild.isType("void")) { // no params
			return;
		}
		this.handleParamList(paramsChild);
	}

	private void handleParamList(BPLNode paramList) throws BPLException {
		BPLNode param = paramList.getChild(0);
		BPLVarNode id;
		if (param.getChild(1).isType("ID")) {
			id = (BPLVarNode) param.getChild(1);
		} else {
			id = (BPLVarNode) param.getChild(2);
		}

		String name = id.getID();
		param.setName(name);
		this.printDebug("Adding PARAM " + param.getName() + " to local decs");
		this.checkVoid(param, name);
		this.localDecs.addFirst(paramList.getChild(0));

		if (paramList.getChildrenSize() == 2) {
			this.handleParamList(paramList.getChild(1));
		}
	}

	private void handleCmpdStmt(BPLNode cmpdStmt) throws BPLException {
		// add local decs to linkedList
		int size = this.addLocalDecs(cmpdStmt.getChild(0));
		this.scopeSizes.push(size);

		// finds references for the statement list
		this.findReferences(cmpdStmt.getChild(1));

		// remove local variables
		this.removeLocalDecs();
	}

	private int addLocalDecs(BPLNode localDecs) throws BPLException {
		if (localDecs.isType("<empty>")) {
			return 0;
		}

		BPLNode varDec = localDecs.getChild(0);
		String varName = getNameFromVarDec(varDec);
		varDec.setName(varName);
		this.checkVoid(varDec, varName);
		this.printDebug("Adding VAR_DEC " + varName + " to local decs");
		this.localDecs.addFirst(varDec);

		return 1 + this.addLocalDecs(localDecs.getChild(1));
	}

	private void removeLocalDecs() {
		int localDecsSize = this.scopeSizes.peek();
		for (int i = 0; i < localDecsSize; i++) {
			BPLNode polled = this.localDecs.poll();
			String polledName = polled.getName();
			this.printDebug("Removing VAR_DEC " + polledName + " from local decs");
		}
		this.scopeSizes.pop();
	}

	private void findReferences(BPLNode stmtList) throws BPLException {
		if (stmtList.isType("<empty>")) {
			return;
		}
		this.findRefStatement(stmtList.getChild(0));
		this.findReferences(stmtList.getChild(1));
	}

	private void findRefStatement(BPLNode statement) throws BPLException {
		BPLNode stmtChild = statement.getChild(0);
		if (stmtChild.isType("IF_STMT")) {
			this.findRefIf(stmtChild);
		} else if (stmtChild.isType("WHILE_STMT")) {
			this.findRefWhile(stmtChild);
		} else if (stmtChild.isType("COMPOUND_STMT")) {
			this.handleCmpdStmt(stmtChild);
		} else if (stmtChild.isType("RETURN_STMT")) {
			this.findRefReturn(stmtChild);
		} else if (stmtChild.isType("WRITE_STMT")) {
			this.findRefWrite(stmtChild);
		} else if (stmtChild.isType("EXPRESSION_STMT")) {
			this.findRefExpStmt(stmtChild);
		} 
	}

	private void findRefIf(BPLNode ifNode) throws BPLException {
		this.findRefExpression(ifNode.getChild(0));
		this.findRefStatement(ifNode.getChild(1));
		if (ifNode.getChildrenSize() > 2) {
			BPLNode el = ifNode.getChild(2);
			this.findRefStatement(el.getChild(0));
		}
	}

	private void findRefWhile(BPLNode whileNode) throws BPLException {
		this.findRefExpression(whileNode.getChild(0));
		this.findRefStatement(whileNode.getChild(1));		
	}

	private void findRefReturn(BPLNode returnNode) throws BPLException {
		if (returnNode.getChildrenSize() > 0) {
			this.findRefExpression(returnNode.getChild(0));
		}
	}

	private void findRefWrite(BPLNode writeNode) throws BPLException {
		if (writeNode.getChildrenSize() > 0) {
			this.findRefExpression(writeNode.getChild(0));
		}
	}

	private void findRefExpStmt(BPLNode expStmt) throws BPLException {
		if (expStmt.getChildrenSize() == 0) {
			return;
		}
		this.findRefExpression(expStmt.getChild(0));
	}

	private String findRefExpression(BPLNode expression) throws BPLException {
		if (expression.getChildrenSize() == 1) { // compexp
			return this.handleCompExp(expression.getChild(0));
		}
		// get reference of vars
		BPLNode var = expression.getChild(0);
		String varName = this.getVarName(var);
		BPLNode ref = this.getVarReference(var, varName);
		String varType = this.linkVarRef(var, varName, ref);

		if (var.getChildrenSize() == 4) { // array assignment
			this.findRefExpression(var.getChild(2));
		}

		String expType = this.findRefExpression(expression.getChild(2));

		if (varType.equals(expType)){
			return varType;
		}
		throw new BPLTypeCheckerException("Types do not match", expression.getLineNumber());
	}

	private BPLNode getVarReference(BPLNode var, String name) throws BPLException {
		for (BPLNode node : this.localDecs) {
			if (node.getName().equals(name)) {
				return node;
			}
		}
		
		if (this.globalDecs.containsKey(name)) {
			return this.globalDecs.get(name);
		}

		throw new BPLTypeCheckerException("Variable " + name + " not defined", var.getLineNumber());
	}

	private String getVarName(BPLNode var) {
		BPLNode child = var.getChild(0);
		if (var.isType("*")) {
			child = var.getChild(1);			
		} 
		return ((BPLVarNode) child).getID();
	}

	private String handleCompExp(BPLNode compExp) throws BPLException {
		String type1 = this.evaluate(compExp.getChild(0));

		if (compExp.getChildrenSize() == 1) {
			return type1;
		}

		String type2 = this.evaluate(compExp.getChild(2));

		if (type1.equals(type2)) {
			return type1;
		}
		throw new BPLTypeCheckerException("Types do not match", compExp.getLineNumber());
	}

	private String evaluate(BPLNode node) throws BPLException {
		if (node.isType("F")) {
			return this.typeF(node);
		}
		String type1 = this.evaluate(node.getChild(0));

		if (node.getChildrenSize() == 1) {
			return type1;
		}

		String type2 = this.evaluate(node.getChild(2));

		// TODO: type check here?
		if (type1.equals(this.TYPE_INT) && type1.equals(this.TYPE_INT)) {
			return type1;
		}
		throw new BPLTypeCheckerException("Types do not match", node.getLineNumber());
	}

	private String typeF(BPLNode f) throws BPLException {
		if (f.getChildrenSize() > 1 && f.getChild(1).isType("F")) {
			String fType = this.typeF(f.getChild(1));
			if (!fType.equals(this.TYPE_INT)) {
				throw new BPLTypeCheckerException("Type " + fType + " cannot be negated", f.getLineNumber());
			}
			return fType;
		}
		String factorType = this.handleFactor(this.getFactor(f));
		// TODO: change type here?
		return factorType;
	}

	private BPLNode getFactor(BPLNode f) {
		BPLNode child = f.getChild(0);
		if (child.isType("FACTOR")) {
			return child;
		}

		return f.getChild(1);
	}

	private String handleFactor(BPLNode factor) throws BPLException {
		BPLNode factChild = factor.getChild(0);

		if (factChild.isType("ID")) {
			String name = ((BPLVarNode) factChild).getID();
			BPLNode ref = this.getVarReference(factChild, name);
			String idType = this.linkVarRef(factChild, name, ref);
			if (factor.getChildrenSize() > 1) { // array
				this.findRefExpression(factor.getChild(2));
			}
			return idType;
		} else if (factChild.isType("EXPRESSION"))	{
			return this.findRefExpression(factChild);
		} else if (factChild.isType("FUN_CALL")) {
			// TODO check params vs args
			return this.getFunRef(factChild);
		} else if (factChild.isType("STRING")) {
			return this.TYPE_STRING;
		} 
		// int or read()
		return this.TYPE_INT;
	}

	private String getFunRef(BPLNode funCall) throws BPLException {
		BPLNode idChild = funCall.getChild(0);
		String id = ((BPLVarNode) idChild).getID();

		if (!this.globalDecs.containsKey(id)) {
			throw new BPLTypeCheckerException("Function " + id + " not defined", funCall.getLineNumber());
		}

		BPLNode funRef = this.globalDecs.get(id);
		this.printDebug("Function call " + id + " on line " + funCall.getLineNumber() + " linked to declaration on line " + funRef.getLineNumber());
		this.compareParamArgs(funRef, funCall, id);

		String funType = this.getFunDecType(funRef);
		this.printDebug("Function call " + id + " on line " + funCall.getLineNumber() + " assigned to type " + funType);
		return funType;
	}

	private void compareParamArgs(BPLNode funRef, BPLNode funCall, String id) throws BPLException {
		BPLNode argsChild = funCall.getChild(1).getChild(0);
		BPLNode paramsChild = funRef.getChild(2).getChild(0);
		if (argsChild.isType("<empty>") && paramsChild.isType("void")) {
			return;
		} else if (argsChild.isType("<empty>") || paramsChild.isType("void")) {
			throw new BPLTypeCheckerException("Arguments of " + id + " does not match declaration", funCall.getLineNumber());
		}
		compareParamArgsHelper(paramsChild, argsChild, id);
	}

	private void compareParamArgsHelper(BPLNode paramList, BPLNode argList, String id) throws BPLException {
		String argType = this.findRefExpression(argList.getChild(0));
		String paramType = this.getVarType(paramList.getChild(0), id);

		if (!argType.equals(paramType) || paramList.getChildrenSize() != argList.getChildrenSize()) {
			throw new BPLTypeCheckerException("Arguments of " + id + " does not match declaration", argList.getLineNumber());
		}

		if (paramList.getChildrenSize() > 1) {
			compareParamArgsHelper(paramList.getChild(1), argList.getChild(1), id);
		}
	}

	private String getFunDecType(BPLNode funDec) {
		BPLNode typeSpec = funDec.getChild(0);
		if (typeSpec.isType("int")) {
			return this.TYPE_INT;
		} else if (typeSpec.isType("void")) {
			return this.TYPE_VOID;
		}
		return this.TYPE_STRING;
	}

	private String linkVarRef(BPLNode node, String id, BPLNode ref) throws BPLException {
		node.setDeclaration(ref);
		this.printDebug("Variable " + id + " on line " + node.getLineNumber() + " linked to declaration on line " + ref.getLineNumber());
		String varType = this.getVarType(ref, id);
		this.printDebug("id node " + id + " on line " + node.getLineNumber() + " assigned type " + varType);
		return varType;
	}

	private String getVarType(BPLNode ref, String id) throws BPLException {
		BPLNode typeSpec = ref.getChild(0);
		if (typeSpec.isType("int")) {
			return this.TYPE_INT;
		} 
		return this.TYPE_STRING;
	}

	private void checkVoid(BPLNode node, String name) throws BPLException {
		if (node.getChild(0).isType("void")) {
			throw new BPLTypeCheckerException("Variable " + name + " cannot be type void", node.getLineNumber());
		}
	}

	private String getNameFromVarDec(BPLNode varDec) {
		BPLNode child = varDec.getChild(1);
		BPLVarNode idChild;
		if (child.isType("ID")) {
			idChild = (BPLVarNode) child;
			return idChild.getID();
		}
		idChild = (BPLVarNode) varDec.getChild(2); 
		return idChild.getID();
	}

	private void printDebug(String message) {
		if (DEBUG) {
			System.out.println(message);
		}
	}

	public static void main(String[] args) throws BPLException {
		if (args.length != 1) {
			System.err.println("File to type check needed!");
			System.exit(1);
		}

		BPLTypeChecker typeChecker = new BPLTypeChecker("../" + args[0]);
	}
}