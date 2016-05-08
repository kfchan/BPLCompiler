package Compiler;

import java.util.*;
import java.io.*;

public class BPLCodeGenerator {
	private final String fp = "%rbx";
	private final String sp = "%rsp";
	private final String gp = "%rdi";
	private final String eax = "%eax";
	private final String ac = "%rax";
	private final String arg2 = "%rsi";
	private final String arg2lower = "%esi";

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
		// for each variable it assigns the current value of count to the position attribute
		// recurse onto the next node in the list with count_1
		// for global, level = 0
			// use the var as a label
		// params, level = 1
			// its offset from fp is 16 + 8(position)
		// compound statments level++;
			// offset from fp is -8 - 8(position)
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
			handleParamsList(paramsList.getChild(1), level, count+1);
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
	}

	private void genCodeCompStatement(BPLNode compStmtNode) {
		this.genCodeLocalDecs(compStmtNode.getChild(0));
		this.genCodeStatementList(compStmtNode.getChild(1));
	}

	private void genCodeLocalDecs(BPLNode localDecsNode) {

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
		}
	}

	private void genCodeWrite(BPLNode writeNode) {
		String print = "$.WritelnString";
		if (writeNode.getChildrenSize() > 0) {
			this.genCodeWriteHelper(writeNode.getChild(0));
			return;
		}

		this.genLineMovq(print, gp, "printf string = arg1");
		this.genLineMovl("$0", eax, "clear return value");
		System.out.println("\tcall printf \t\t# call printf");
	}

	private void genCodeWriteHelper(BPLNode writeExpNode) {
		if (writeExpNode.getEvalType().equals(BPLTypeChecker.TYPE_STRING)) {
			String s = this.getString(writeExpNode);
			this.genLineMovq("$" + this.stringMap.get(s), ac, "putting string value into ac");
			this.genLineMovq(ac, arg2, "putting string to print to arg2");
			this.genLineMovq("$.WriteStringString", gp, "printf string to arg1");
			this.genLineMovl("$0", eax, "clear return value");
			System.out.println("\tcall printf \t\t# call printf");
		} else if (writeExpNode.getEvalType().equals(BPLTypeChecker.TYPE_INT)) {
			int i = this.getInteger(writeExpNode);
			this.genLineMovq("$" + i, ac, "putting value into ac");
			this.genLineMovl(eax, arg2lower, "putting value to print to arg2");
			this.genLineMovq("$.WriteIntString", gp, "printf string to arg1");
			this.genLineMovl("$0", eax, "clear return value");	
			System.out.println("\tcall printf \t\t# call printf");		
		}
		// TODO: other types (pointers)
	}

	private String getString(BPLNode node) {
		if (!node.isType("STRING")) {
			return this.getString(node.getChild(0));
		}
		return node.getChild(0).getType();
	}

	private int getInteger(BPLNode node) {
		if (!node.isType("INTEGER")) {
			return this.getInteger(node.getChild(0));
		}

		return ((BPLIntegerNode) node).getInteger();
	}

	private void genCodeExpression(BPLNode expNode) {

	}

	private void genCodeFunctionDec(BPLNode funDecNode) {

	}

	private void genLineMovq(String firstArg, String secondArg, String comment) {
		System.out.println("\tmovq " + firstArg + ", " + secondArg + "\t\t# " + comment);
	}

	private void genLineMovl(String firstArg, String secondArg, String comment) {
		System.out.println("\tmovl " + firstArg + ", " + secondArg + "\t\t# " + comment);
	}

	public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, BPLException {
		if (args.length != 1) {
			System.err.println("File to type check needed!");
			System.exit(1);
		}

		BPLCodeGenerator generator = new BPLCodeGenerator("../" + args[0]);
	}
}