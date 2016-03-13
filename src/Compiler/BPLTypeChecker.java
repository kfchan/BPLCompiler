package Compiler;

import java.util.*;

public class BPLTypeChecker {
	private static final boolean DEBUG = true;

	private BPLParser parser;
	private BPLNode parseTree;

	public BPLTypeChecker(String filename) throws BPLException {
		this.parser = new BPLParser(filename);
		this.parseTree = this.parser.getBPLHead();
	}
	
	public static void main(String[] args) throws BPLException {
		if (args.length != 1) {
			System.err.println("File to type check needed!");
			System.exit(1);
		}

		BPLTypeChecker typeChecker = new BPLTypeChecker("../" + args[0]);
	}
}