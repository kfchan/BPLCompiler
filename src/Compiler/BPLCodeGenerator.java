package Compiler;

import java.util.*;
import java.io.*;

public class BPLCodeGenerator {
	private BPLNode parseTreeHead;
	private BPLTypeChecker typeChecker;
	private HashMap<String, String> stringMap;

	public BPLCodeGenerator(String fileName) throws FileNotFoundException, UnsupportedEncodingException, BPLException {
		this.typeChecker = new BPLTypeChecker(fileName);
		this.parseTreeHead = this.typeChecker.getParseTreeHead();
		this.stringMap = new HashMap<String,String>();
		this.getDepthsAndHeader();
		this.generateCode();
	}

	private void getDepthsAndHeader() {
		if (this.parseTreeHead.getChildrenSize() == 0) {
			return;
		}
		findDepthDeclaration(this.parseTreeHead.getChild(0));
	}

	private void findDepthDeclaration(BPLNode decList) {
		this.handleGlobals(decList);
		this.findDepthDeclaration(decList, 0, 0);
	}

	/**
	* walks along a list of declarations
	*/ 
	private void findDepthDeclaration(BPLNode node, int level, int count) {
		if (level == 0) {
			this.handleGlobalDepths(node, level, count);
		} else if (level == 1) {
			this.handleParams(node, level, count);
		} else {
			handleCompoundDepth(node, level, count);
		}
	}

	private void handleCompoundDepth(BPLNode compStmt, int level, int count) {
		BPLNode localDecNode = compStmt.getChild(0);
		count = this.handleLocalDecDepths(localDecNode, level, count);
		this.handleStatementListDepth(compStmt.getChild(1), level, count);
	}

	private void handleStatementListDepth(BPLNode stmtList, int level, int count) {
		// System.out.println("statement list depths type : " + stmtList);
		if (stmtList.isType("<empty>")) {
			return;
		}

		this.handleStatementDepth(stmtList.getChild(0), level, count);
		// this.handleStatement(stmtList.getChild(0).getChild(0), level, count);
		this.handleStatementListDepth(stmtList.getChild(1), level, count);	
	}

	private void handleStatementDepth(BPLNode statementNode, int level, int count) {
		// System.out.println("statementNode type:  " + statementNode.getType());

		this.handleStatement(statementNode.getChild(0), level, count);
	}

	private void handleStatement(BPLNode statementChild, int level, int count) {
		// System.out.println("statementChild type: " + statementChild.getType());
		if (statementChild.isType("IF_STMT") || statementChild.isType("WHILE_STMT")) {
			this.handleStatementDepth(statementChild.getChild(1), level, count);
			if (statementChild.getChildrenSize() > 2) {
				this.handleStatementDepth(statementChild.getChild(2), level, count);
			}
		} else if (statementChild.isType("COMPOUND_STMT")) {
			this.handleCompoundDepth(statementChild, level + 1, count);
		}
	}

	private int handleLocalDecDepths(BPLNode localDecNode, int level, int count) {
		// System.out.println("localdec depths type: " + localDecNode.getType());		
		if (localDecNode.isType("<empty>")) {
			return count;
		}

		BPLNode varDec = localDecNode.getChild(0);

		// System.out.println("localdec " + varDec.getName() + " " + level + " " + count);
		varDec.assignDepth(level);
		varDec.assignPosition(count);

		return this.handleLocalDecDepths(localDecNode.getChild(1), level, count + 1);
	}

	private void handleGlobalDepths(BPLNode decList, int level, int count) {
		BPLNode decNode = decList.getChild(0);
		BPLNode decNodeChild = decNode.getChild(0);
		if (decNodeChild.isType("VAR_DEC")) {
			// System.out.println("global vardec " + decNodeChild.getName() + " " + level + " " + count);
			decNodeChild.assignDepth(level);
			decNodeChild.assignPosition(count);
		} else {
			// System.out.println("global fundec " + decNodeChild.getName() + " " + level + " " + count);
			decNodeChild.assignDepth(level);
			decNodeChild.assignPosition(count);
			this.findDepthDeclaration(decNodeChild.getChild(2), level+1, count);
			this.findDepthDeclaration(decNodeChild.getChild(3), level+2, count);
		}

		if (decList.getChildrenSize() > 1) {
			findDepthDeclaration(decList.getChild(1), level, count);
		}
	}

	private void handleParams(BPLNode params, int level, int count) {
		if (!params.getChild(0).isType("void")) {
			this.handleParamsList(params.getChild(0), level, count);
		}
	}

	private void handleParamsList(BPLNode paramsList, int level, int count) {
		BPLNode paramNode = paramsList.getChild(0);
		// System.out.println("params " + paramNode.getName() + " " + level + " " + count);
		paramNode.assignDepth(level);
		paramNode.assignPosition(count);
		if (paramsList.getChildrenSize() > 1) {
			handleParamsList(paramsList.getChild(1), level, count + 1);
		}
	}

	private void handleGlobals(BPLNode decList) {
		this.initializeGlobalVars(decList);
		System.out.println(".section .rodata\n" + 
			".WriteIntString: .string \"%d\"\n" + 
			".WriteStringString: .string \"%s\"\n" + 
			".WritelnString: .string \"\\n\"");

		this.initializeStringConstants();

		System.out.println(".text \n" + 
			".globl main\n");
	}	

	private void initializeGlobalVars(BPLNode decList) {
		HashMap<String, BPLNode> globals = this.typeChecker.getGlobals();

		for (String varName : globals.keySet()) {
			BPLNode node = globals.get(varName);
			if (node.isType("VAR_DEC")) {
				this.genGlobalVar(varName, node);
			}
		}
	}

	private void genGlobalVar(String name, BPLNode node) {
		if (node.getChild(0).isType("*")) { // skip because i dunno pointers
			return;
		}
		int spaceAl = 8;
		// System.out.println(node.getChildrenSize());
		if (node.getChildrenSize() == 5) { // TODO: why is the size 5..
			BPLIntegerNode intNode = (BPLIntegerNode) node.getChild(3);
			spaceAl *= intNode.getInteger();
		}
		System.out.println(".comm " + name + ", " + spaceAl + ", 32");
	}

	private void initializeStringConstants() {
		ArrayList<String> strings = this.typeChecker.getStrings();
		for (int i = 0; i < strings.size(); i++) {
			String s = strings.get(i);
			System.out.println(".Potato" + i + ": .string "  + s);
			stringMap.put(s, ".Potato" + i);
		}
	}

	private void generateCode() {
		if (this.parseTreeHead.getChildrenSize() != 0) {
			// System.out.println("generateCode");
			this.genCodeDecList(this.parseTreeHead.getChild(0));
		}
	}

	private void genCodeDecList(BPLNode decListNode) {
		// TODO: only handles functions with no params or local decs
		BPLNode decNode = decListNode.getChild(0);
		if (decNode.getChild(0).isType("FUN_DEC")) {
			// System.out.println("genCodeDecList FUN_DEC");
			this.genCodeFunDec(decNode.getChild(0));
		}
		if (decListNode.getChildrenSize() > 1) {
			this.genCodeDecList(decListNode.getChild(1));
		}
	}

	private void genCodeFunDec(BPLNode funDecNode) {
		BPLVarNode idNode = (BPLVarNode) funDecNode.getChild(1);
		System.out.println(idNode.getID() + ":");

		this.genCodeCompStatement(funDecNode.getChild(3));
		// TODO: change 0 to the actual space allocated
		this.print("add $0, %rsp", "deallocate local variables");
		this.print("ret");
	}

	private void genCodeCompStatement(BPLNode compStmtNode) {
		this.genCodeLocalDecs(compStmtNode.getChild(0));
		this.genCodeStatementList(compStmtNode.getChild(1));
	}

	private void genCodeLocalDecs(BPLNode localDecsNode) {
		this.print("movq %rsp, %rbx", "setup fp");
	}

	private void genCodeStatementList(BPLNode stmtListNode) {
		// System.out.println("stmtList " + stmtListNode.getType());
		if (stmtListNode.isType("<empty>")) {
			return;
		}

		this.genCodeStatement(stmtListNode.getChild(0));
		this.genCodeStatementList(stmtListNode.getChild(1));
	}

	private void genCodeStatement(BPLNode statementNode) {
		BPLNode statementChildNode = statementNode.getChild(0);

		if (statementChildNode.isType("WRITE_STMT")) {
			this.genCodeWrite(statementChildNode);
		} else if (statementChildNode.isType("EXPRESSION_STMT")) {
			this.genCodeExpressionStmt(statementChildNode);
		} else if (statementChildNode.isType("IF_STMT")) {
			this.genCodeIfStatement(statementChildNode);
		} else if (statementChildNode.isType("WHILE_STMT")) {
			this.genCodeWhileStatement(statementChildNode);
		} else if (statementChildNode.isType("RETURN_STMT")) {
			this.genCodeReturnStatement(statementChildNode);
		} else {
			this.genCodeCompStatement(statementChildNode);
		}
	}

	private void genCodeIfStatement(BPLNode ifNode) {
		this.genCodeExpression(ifNode.getChild(0));

		this.genCodeStatement(ifNode.getChild(1));

		if (ifNode.getChildrenSize() > 2) {
			this.genCodeStatement(ifNode.getChild(2));
		}
	}

	private void genCodeWhileStatement(BPLNode whileNode) {
		this.genCodeExpression(whileNode.getChild(0));

		this.genCodeStatement(whileNode.getChild(1));
	}

	private void genCodeReturnStatement(BPLNode returnNode) {
		if (returnNode.getChildrenSize() > 0) {
			this.genCodeExpression(returnNode.getChild(0));
		}

		System.out.println("ret");
	}

	private void genCodeWrite(BPLNode writeNode) {
		String print = "$.WritelnString";
		if (writeNode.getChildrenSize() > 0) {
			this.genCodeWriteHelper(writeNode.getChild(0));
			return;
		}

		this.print("movq " + print + ", %rdi", "printf string = arg1");
		this.print("movl $0, %eax", "clear return value");
		System.out.println("\tcall printf \t\t# call printf");
	}

	private void genCodeWriteHelper(BPLNode writeExpNode) {
		if (writeExpNode.getEvalType().equals(BPLTypeChecker.TYPE_STRING)) {
			this.genCodeExpression(writeExpNode);
			this.print("movq %rax, %rsi", "putting string to print to arg2");
			this.print("movq $.WriteStringString, %rdi", "printf string to arg1");		} else if (writeExpNode.getEvalType().equals(BPLTypeChecker.TYPE_INT)) {
			this.genCodeExpression(writeExpNode);
			this.print("movl %eax, %esi", "putting value to print to arg2");
			this.print("movq $.WriteIntString, %rdi", "printf string to arg1");
		}
		this.print("movl $0, %eax", "clear return value");
		this.print("call printf", "call printf");	
	}

	private void genCodeExpressionStmt(BPLNode expStmtNode) {
		if (expStmtNode.getChildrenSize() > 0) {
			this.genCodeExpression(expStmtNode.getChild(0));
		}
	}

	private void genCodeExpression(BPLNode expNode) {
		// System.out.println("expNode " + expNode.getType());
		if (expNode.getChild(0).isType("COMP_EXP")) {
			this.genCodeCompExp(expNode.getChild(0));
		} else {
			// o.w the expression is an assignment
			this.genCodeAssignment(expNode);
		}
	}

	private void genCodeCompExp(BPLNode compExpNode) {
		// System.out.println("compExpNode " + compExpNode.getType());	
		if (compExpNode.getChildrenSize() == 1) {
			this.genCodeENode(compExpNode.getChild(0));
		}

		// TODO
	}

	private void genCodeAssignment(BPLNode expNode) {
		BPLNode var = expNode.getChild(0);
		BPLNode varDec = var.getDeclaration();

		// System.out.println(var.getType());
		String id = ((BPLVarNode) var.getChild(0)).getID();

		this.genCodeExpression(expNode.getChild(2));

		if (varDec.getDepth() == 0) {

			this.print("movq %rax, " + id);
		} else {

		}
	}

	private String genCodeExpressionString(BPLNode node) {
		if (!node.isType("STRING")) {
			return this.genCodeExpressionString(node.getChild(0));
		}
		return node.getChild(0).getType();
	}

	private void genCodeENode(BPLNode eNode) {
		// System.out.println("eNode " + eNode.getType());
		if (eNode.getChildrenSize() == 1) {
			this.genCodeTNode(eNode.getChild(0));
			return;
		}
		this.genCodeTNode(eNode.getChild(2));
		this.print("push %rax", "addop");
		this.genCodeENode(eNode.getChild(0));

		BPLNode addop = eNode.getChild(1);
		if (addop.getChild(0).getType().equals("+")) {
			this.print("addq 0(%rsp), %rax", "addition with top of stack");
		} else {
			this.print("subq 0(%rsp), %rax", "subtraction with top of stack");
		}

		this.print("addq $8, %rsp", "pop off stack");
	}	

	private void genCodeTNode(BPLNode tNode) {
		// System.out.println("tNode " + tNode.getType());
		if (tNode.getChildrenSize() == 1) {
			this.genCodeFNode(tNode.getChild(0));
			return;
		}

		this.genCodeFNode(tNode.getChild(2));
		BPLNode mulop = tNode.getChild(1);
		if (mulop.getChild(0).getType().equals("*")) {
			this.print("push %rax", "mulop");
			this.genCodeTNode(tNode.getChild(0));
			this.print("imul 0(%rsp), %eax", "multiplication with top of stack");
			this.print("addq $8, %rsp", "pop off stack");
		} else {
			this.print("movl %eax, %ebp", "divisor to ebp");
			this.genCodeTNode(tNode.getChild(0));
			this.print("cltq");
			this.print("cqto");
			this.print("idivl %ebp");
			if (mulop.getChild(0).getType().equals("%")) {
				this.print("movl %edx, %eax", "remainder to eax");
			}
		} 
	}

	private void genCodeFNode(BPLNode fNode) {
		//  System.out.println("fNode " + fNode.getType());
		// TODO: -F, &Factor, *Factor
		BPLNode fChild = fNode.getChild(0);
		if (fChild.isType("FACTOR")) {
			genCodeFactorNode(fNode.getChild(0));
		} else if (fChild.isType("-")) {
			this.genCodeFNode(fNode.getChild(1));
			this.print("neg %eax");
		} else if (fChild.isType("*")) {
			// TODO:
		} else {
			// TODO:
		}
	}

	private void genCodeFactorNode(BPLNode factorNode) {
		// System.out.println("factorNode " + factorNode.getType());
		BPLNode factorChild = factorNode.getChild(0);
		if (factorChild.isType("EXPRESSION")) {
			this.genCodeExpression(factorChild);
		} else if (factorChild.isType("FUN_CALL")) {
			this.genCodeFunCall(factorChild);
		} else if (factorChild.isType("READ")) {

		} else if (factorChild.isType("INTEGER")) {
			int val = ((BPLIntegerNode) factorChild).getInteger();
			this.print("movq $" + val + ", %rax", "putting value into ac");
		} else if (factorChild.isType("STRING")) {
			String s = factorChild.getChild(0).getType();
			this.print("movq $" + this.stringMap.get(s) + ", %rax", "putting string value into ac");
		} else { 
			this.genCodeFactorID(factorNode);
		}
	}

	private void genCodeFactorID(BPLNode factorID) {
		// TODO: locals

		BPLNode varDec = factorID.getChild(0).getDeclaration();
		String id = ((BPLVarNode) factorID.getChild(0)).getID();
		if (varDec.getDepth() == 0) {
			this.print("movq " + id + ", %rax");
		}
	}

	private void genCodeFunCall(BPLNode funCallNode) {
		BPLNode idNode = funCallNode.getChild(0);
		String id = ((BPLVarNode) idNode).getID();
		BPLNode funDec = idNode.getDeclaration(); 
		BPLNode args = funCallNode.getChild(1);

		int space = 0;
		if (args.getChild(0).isType("ARG_LIST")) {
			space = this.genCodeFunCallArgs(args.getChild(0), 0);
		}
		this.print("push %rbx", "push frame pointer");
		this.print("call " + id);
		this.print("pop %rbx");

		if (args.getChild(0).isType("ARG_LIST")) {
			// TODO: change 8 to be the actual space of args in stack
			this.print("add $" + space + ", %rsp", "removing args from the stack");
		}
	}

	private int genCodeFunCallArgs(BPLNode argList, int spaceAl) {
		this.genCodeExpression(argList.getChild(0));
		this.print("push %rax");

		if (argList.getChildrenSize() > 1) {
			// recusive call
			spaceAl = genCodeFunCallArgs(argList.getChild(1), spaceAl);
		}

		// TODO: the 8 will probably change?
		return spaceAl + 8;
	}

	private void print(String code) {
		System.out.println("\t" + code);
	}

	private void print(String code, String comment) {
		System.out.println("\t" + code + "\t\t# " + comment);
	}

	public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, BPLException {
		if (args.length != 1) {
			System.err.println("File to type check needed!");
			System.exit(1);
		}

		BPLCodeGenerator generator = new BPLCodeGenerator("../" + args[0]);
	}
}