package Compiler;

import java.util.*;

public class BPLTypeChecker {
	private static final boolean DEBUG = true;

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

	private void addToGlobalDecs(BPLNode decChild) {
		String varName = this.getNameFromVarDec(decChild);
		this.printDebug("Adding VAR_DEC " + varName + " to global");
		this.globalDecs.put(varName, decChild);
	}

	private void addFunToDecs(BPLNode funDec) {
		BPLNode idNode = funDec.getChild(1);
		String id = ((BPLVarNode) idNode).getID();
		this.printDebug("Adding FUN_DEC " + id + " to global");
		this.globalDecs.put(id, funDec);
	}

	private void handleFunDec(BPLNode funDec) throws BPLException {
		// create new local decs
		printDebug("New local decs list");
		this.localDecs.clear();

		// params
		this.addParamsToLocal(funDec.getChild(2));

		// compound statement
		this.handleCmpdStmt(funDec.getChild(3));
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
		param.setName(id.getID());

		this.printDebug("Adding PARAM " + param.getName() + " to local decs");
		this.localDecs.addFirst(paramList.getChild(0));

		if (paramList.getChildrenSize() == 2) {
			this.handleParamList(paramList.getChild(1));
		}
	}

	private void handleCmpdStmt(BPLNode cmpdStmt) throws BPLException {
		// add local decs to linkedList
		this.addLocalDecs(cmpdStmt.getChild(0));

		// finds references for the statement list
		this.findReferences(cmpdStmt.getChild(1));
	}

	private void addLocalDecs(BPLNode localDecs) throws BPLException {
		if (localDecs.isType("<empty>")) {
			return;
		}

		BPLNode varDec = localDecs.getChild(0);
		String varName = getNameFromVarDec(varDec);
		varDec.setName(varName);
		this.printDebug("Adding VAR_DEC " + varName + " to local decs");
		this.localDecs.addFirst(varDec);

		this.addLocalDecs(localDecs.getChild(1));
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

	private void findRefExpression(BPLNode expression) throws BPLException {
		if (expression.getChildrenSize() == 1) { // compexp
			this.handleCompExp(expression.getChild(0));
			return;
		}
		// get reference of vars
		BPLNode var = expression.getChild(0);
		String varName = this.getVarName(var);
		BPLNode ref = this.getVarReference(var, varName);
		this.linkVarRef(var, varName, ref);

		if (var.getChildrenSize() == 4) {
			this.findRefExpression(var.getChild(2));
		}

		this.findRefExpression(expression.getChild(2));
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

	private void handleCompExp(BPLNode compExp) throws BPLException {
		this.evaluate(compExp.getChild(0));

		if (compExp.getChildrenSize() == 1) {
			return;
		}

		this.evaluate(compExp.getChild(2));
	}

	private void evaluate(BPLNode node) throws BPLException {
		if (node.isType("F")) {
			this.typeF(node);
			return;
		}
		this.evaluate(node.getChild(0));

		if (node.getChildrenSize() == 1) {
			return;
		}

		this.evaluate(node.getChild(2));
	}

	private void typeF(BPLNode f) throws BPLException {
		if (f.getChildrenSize() > 1 && f.getChild(1).isType("F")) {
			this.typeF(f.getChild(1));
			return;
		}
		this.handleFactor(this.getFactor(f));
	}

	private BPLNode getFactor(BPLNode f) {
		BPLNode child = f.getChild(0);
		if (child.isType("FACTOR")) {
			return child;
		}

		return f.getChild(1);
	}

	private void handleFactor(BPLNode factor) throws BPLException {
		BPLNode factChild = factor.getChild(0);

		if (factChild.isType("ID")) {
			String name = ((BPLVarNode) factChild).getID();
			BPLNode ref = this.getVarReference(factChild, name);
			this.linkVarRef(factChild, name, ref);
			if (factor.getChildrenSize() > 1) { // array
				this.findRefExpression(factor.getChild(2));
			}
		} else if (factChild.isType("EXPRESSION"))	{
			this.findRefExpression(factChild);
		} else if (factChild.isType("FUN_CALL")) {
			this.getFunRef(factChild);
		}
	}

	private void getFunRef(BPLNode funCall) throws BPLException {
		BPLNode idChild = funCall.getChild(0);
		String id = ((BPLVarNode) idChild).getID();

		if (!this.globalDecs.containsKey(id)) {
			throw new BPLTypeCheckerException("Function " + id + " not defined", funCall.getLineNumber());
		}

		BPLNode funRef = this.globalDecs.get(id);
		this.printDebug("Function call " + id + " on line " + funCall.getLineNumber() + " linked to declaration on line " + funRef.getLineNumber());
	}

	private void linkVarRef(BPLNode node, String id, BPLNode ref) {
		node.setDeclaration(ref);
		this.printDebug("Variable " + id + " on line " + node.getLineNumber() + " linked to declaration on line " + ref.getLineNumber());
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