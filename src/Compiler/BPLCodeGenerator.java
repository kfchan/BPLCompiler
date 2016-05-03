package Compiler;

import java.util.*;
import java.io.*;

public class BPLCodeGenerator {
	private BPLNode parseTreeHead;
	private BPLTypeChecker typeChecker;

	public BPLCodeGenerator(String fileName) throws FileNotFoundException, UnsupportedEncodingException, BPLException {
		this.typeChecker = new BPLTypeChecker(fileName);
		this.parseTreeHead = this.typeChecker.getParseTreeHead();
		this.generateCode();
	}

	private void generateCode() {
		this.initializeCode();
	}

	private void initializeCode() {
		System.out.println(".section .rodata \n" + 
			".WriteIntString: .string \"%d \" \n" + 
			".WritelnString: .string \"\\n\" \n" + 
			".text \n" + 
			".globl main");
	}

	public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, BPLException {
		if (args.length != 1) {
			System.err.println("File to type check needed!");
			System.exit(1);
		}

		BPLCodeGenerator typeChecker = new BPLCodeGenerator("../" + args[0]);
	}
}