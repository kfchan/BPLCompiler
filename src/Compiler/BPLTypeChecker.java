package Compiler;

import java.util.*;

public class BPLTypeChecker {
	private static final boolean DEBUG = true;

	private BPLParser parser;
	private BPLNode parseTree;
	private HashMap<String,BPLNode> decs;

	public BPLTypeChecker(String filename) throws BPLException {
		this.parser = new BPLParser(filename);
		this.parseTree = this.parser.getBPLHead();
		this.decs = new HashMap<String,BPLNode>();
		this.findReferences(this.parseTree);
	}

	private void findReferences(BPLNode head) throws BPLException {
		BPLNode decList = this.getDecList(head);
		this.findRefHelper(decList);
	}

	private void findRefHelper(BPLNode decList) {
		BPLNode dec = decList.getChild(0);
		BPLNode decChild = dec.getChild(0);
		
		if (decChild.isType("VAR_DEC")) {
			this.addToDecs(dec, decChild);
		} else {
			System.out.println("FUN_DEC");
		}
		
		if (decList.isChildrenSize(2)) {
			findRefHelper(decList.getChild(1));
		}
	}

	private BPLNode getDecList(BPLNode head) throws BPLException {
		if (head.isChildrenSize(0)) {
			this.printDebug("Nothing in BPL file.");
			System.exit(0);
		}
		return head.getChild(0);
	}

	private void addToDecs(BPLNode dec, BPLNode decChild) {
		String varName = this.getNameFromDec(decChild);
		this.decs.put(varName, dec);
	}

	private String getNameFromDec(BPLNode decChild) {
		BPLNode child = decChild.getChild(1);
		BPLVarNode idChild;
		if (child.isType("ID")) {
			idChild = (BPLVarNode) child;
			return idChild.getID();
		}
		idChild = (BPLVarNode) decChild.getChild(2); 
		return idChild.getID();
	}
	
	private void printDecs() {
		for (String varName : this.decs.keySet()) {
			System.out.println(varName);
		}
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
		typeChecker.printDecs();
	}
}