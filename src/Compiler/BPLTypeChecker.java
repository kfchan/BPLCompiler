package Compiler;

import java.util.*;

public class BPLTypeChecker {
	private static final boolean DEBUG = true;
	private static final String TYPE_VOID = "void";
	private static final String TYPE_INT = "int";
	private static final String TYPE_STRING = "string";
	private static final String TYPE_PTRINT = "pointer to integer";
	private static final String TYPE_PTRSTRING = "pointer to string";
	private static final String TYPE_ADDINT = "address of integer";
	private static final String TYPE_ADDSTRING = "address of integer";

	private final BPLParser parser;
	
	private BPLNode parseTree;
	private BPLNode currFunDec;
	private HashMap<String, BPLNode> globalDecs;
	private LinkedList<BPLNode> localDecs; 
	private Stack<Integer> scopeSizes;
	private boolean isArg;

	public BPLTypeChecker(String filename) throws BPLException {
		this.parser = new BPLParser(filename);
		this.parseTree = this.parser.getBPLHead();
		this.globalDecs = new HashMap<String, BPLNode>();
		this.localDecs = new LinkedList<BPLNode>();
		this.scopeSizes = new Stack<Integer>();
		this.isArg = false;
		this.currFunDec = null;
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
		if (this.globalDecs.containsKey(varName)) {
			throw new BPLTypeCheckerException("ID " + varName + " already declared globally");
		}
		this.checkVoid(decChild, varName);
		this.globalDecs.put(varName, decChild);
	}

	private void addFunToDecs(BPLNode funDec) throws BPLException {
		this.currFunDec = funDec;
		String id = getFunDecID(funDec);
		if (this.globalDecs.containsKey(id)) {
			throw new BPLTypeCheckerException("ID " + id + " already declared globally");
		}
		this.globalDecs.put(id, funDec);
	}

	private String getFunDecID(BPLNode funDec) {
		BPLNode idNode = funDec.getChild(1);
		String id = ((BPLVarNode) idNode).getID();
		return id;
	}

	private void handleFunDec(BPLNode funDec) throws BPLException {
		// create new local decs
		this.localDecs.clear();

		// params
		this.addParamsToLocal(funDec.getChild(2));

		// compound statement
		this.handleCmpdStmt(funDec.getChild(3));

		// clear localdecs (remove params)
		this.localDecs.clear();
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
		this.localDecs.addFirst(varDec);

		return 1 + this.addLocalDecs(localDecs.getChild(1));
	}

	private void removeLocalDecs() {
		int localDecsSize = this.scopeSizes.peek();
		for (int i = 0; i < localDecsSize; i++) {
			this.localDecs.poll();
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
		} else {
			this.findRefExpStmt(stmtChild);
		} 
	}

	private void findRefIf(BPLNode ifNode) throws BPLException {
		String expType = this.findRefExpression(ifNode.getChild(0));
		if (!expType.equals(this.TYPE_INT)) {
			throw new BPLTypeCheckerException("Condition must be of type int", ifNode.getLineNumber());
		}
		this.findRefStatement(ifNode.getChild(1));
		if (ifNode.getChildrenSize() > 2) {
			this.findRefStatement(ifNode.getChild(2));
		}
	}

	private void findRefWhile(BPLNode whileNode) throws BPLException {
		String expType = this.findRefExpression(whileNode.getChild(0));
		if (!expType.equals(this.TYPE_INT)) {
			throw new BPLTypeCheckerException("Condition must be of type int", whileNode.getLineNumber());
		}
		this.findRefStatement(whileNode.getChild(1));		
	}

	private void findRefReturn(BPLNode returnNode) throws BPLException {
		String expectedType = this.getVarType(this.currFunDec);
		String id = this.getFunDecID(this.currFunDec);
		if (returnNode.getChildrenSize() > 0) {
			String expType = this.findRefExpression(returnNode.getChild(0));
			if (!expType.equals(expectedType)) {
				throw new BPLTypeCheckerException("Function " + id + " has return type " + expectedType, returnNode.getLineNumber());
			}
			return;
		}
		if (!expectedType.equals(this.TYPE_VOID)) {
			throw new BPLTypeCheckerException("Function " + id + " has return type " + expectedType, returnNode.getLineNumber());
		}
	}

	private void findRefWrite(BPLNode writeNode) throws BPLException {
		if (writeNode.getChildrenSize() > 0) {
			String expType = this.findRefExpression(writeNode.getChild(0));
			if (!(expType.equals(this.TYPE_INT) || expType.equals(this.TYPE_STRING))) {
				throw new BPLTypeCheckerException("write() can only accept type string or int as argument", writeNode.getLineNumber());
			}
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
		varType = this.checkPointer(var, varType);

		if (var.getChildrenSize() == 4) { // array assignment
			String arrayIndexType = this.findRefExpression(var.getChild(2));
			if (!arrayIndexType.equals(this.TYPE_INT)) {
				throw new BPLTypeCheckerException("Arrays are indexed by integer types only", var.getChild(2).getLineNumber());
			}
		}

		String expType = this.findRefExpression(expression.getChild(2));

		if (varType.equals(expType) || 
			(varType.equals(this.TYPE_PTRSTRING) && expType.equals(this.TYPE_ADDSTRING) && var.getChildrenSize() == 1) ||
			(varType.equals(this.TYPE_PTRINT) && expType.equals(this.TYPE_ADDINT) && var.getChildrenSize() == 1)) {
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
		if (child.isType("*")) {
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
			// comparators return ints?
			return this.TYPE_INT;
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
		this.handleOp(node.getChild(1));

		String type2 = this.evaluate(node.getChild(2));

		if (type1.equals(this.TYPE_INT) && type2.equals(this.TYPE_INT)) {
			return type1;
		}
		throw new BPLTypeCheckerException("Types do not match", node.getLineNumber());
	}

	private void handleOp(BPLNode opNode) {
		String op = opNode.getChild(0).getType();
		this.printDebug(op + " (op) node on line " + opNode.getLineNumber() + " assigned type " + this.TYPE_INT);
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
		return this.checkPointer(f, factorType);
	}

	private String checkPointer(BPLNode f, String origFactorType) throws BPLException {
		BPLNode child = f.getChild(0);
		if (child.isType("&") && origFactorType.equals(this.TYPE_PTRINT)) {
			return this.TYPE_ADDINT;
		} else if (child.isType("*") && origFactorType.equals(this.TYPE_PTRINT)) {
			return this.TYPE_INT;
		} else if (child.isType("&") && origFactorType.equals(this.TYPE_PTRSTRING)) {
			return this.TYPE_ADDSTRING;
		} else if (child.isType("*") && origFactorType.equals(this.TYPE_PTRSTRING)) {
			return this.TYPE_STRING;
		} else if (origFactorType.equals(this.TYPE_PTRINT) || 
			origFactorType.equals(this.TYPE_PTRSTRING) || 
			origFactorType.equals(this.TYPE_INT) || 
			origFactorType.equals(this.TYPE_STRING) || 
			origFactorType.equals(this.TYPE_VOID)) {
			return origFactorType;
		}
		throw new BPLTypeCheckerException("Incorrect pointer usage", f.getLineNumber());
		
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
				String arrayIndexType = this.findRefExpression(factor.getChild(2));
				if (!arrayIndexType.equals(this.TYPE_INT)) {
					throw new BPLTypeCheckerException("Arrays are indexed by integer types only", factor.getChild(2).getLineNumber());
				}
			}
			return idType;
		} else if (factChild.isType("EXPRESSION"))	{
			return this.findRefExpression(factChild);
		} else if (factChild.isType("FUN_CALL")) {
			return this.getFunRef(factChild);
		} else if (factChild.isType("STRING")) {
			this.printDebug(factChild.getChild(0).getType() + " (string) node on line " + factChild.getLineNumber() + " assigned type " + this.TYPE_STRING);
			return this.TYPE_STRING;
		} else if (factChild.isType("INTEGER")) {
			this.printDebug(((BPLIntegerNode) factChild).getInteger() + " (integer) node on line " + factChild.getLineNumber() + " assigned type " + this.TYPE_INT);
			return this.TYPE_INT;
		}
		// read()
		this.printDebug("read() node on line" + factChild.getLineNumber() + " assigned type " + this.TYPE_INT);
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
		this.printDebug(id + " (function call) node on line " + funCall.getLineNumber() + " assigned to type " + funType);
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
		this.isArg = true;
		compareParamArgsHelper(paramsChild, argsChild, id);
		this.isArg = false;
	}

	private void compareParamArgsHelper(BPLNode paramList, BPLNode argList, String id) throws BPLException {
		String argType = this.findRefExpression(argList.getChild(0));
		String paramType = this.getVarType(paramList.getChild(0));

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
		String varType = this.getVarType(ref);
		this.printDebug(id + " (id node) on line " + node.getLineNumber() + " assigned type " + varType);
		return varType;
	}

	private String getVarType(BPLNode ref) throws BPLException {
		BPLNode typeSpec = ref.getChild(0);
		BPLNode child1 = ref.getChild(1);
		if (typeSpec.isType("int") && child1.isType("*")) {
			return this.TYPE_PTRINT;
		} else if (typeSpec.isType("string") && child1.isType("*")) {
			return this.TYPE_PTRSTRING;
		} else if (typeSpec.isType("int")) {
			return this.TYPE_INT;
		} else if (typeSpec.isType("void")) {
			return this.TYPE_VOID;
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