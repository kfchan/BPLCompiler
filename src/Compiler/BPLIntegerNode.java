package Compiler;

import java.io.*;
import java.util.*;

public class BPLIntegerNode extends BPLNode {
	private int lineNumber;
	private int num;
	private BPLNode parent;

	public BPLIntegerNode(int num, int lineNumber) {
		super("INTEGER", lineNumber);
		this.num = num;
		this.lineNumber = lineNumber;
	}

	@Override
	public String toString() {
		String rtn = "Line " + this.getLineNumber() + ": Integer " + num + "\n";
		return rtn;
	}

}