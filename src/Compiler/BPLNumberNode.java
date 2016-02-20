package Compiler;

import java.io.*;
import java.util.*;

public class BPLNumberNode extends BPLNode {
	private int lineNumber;
	private int num;
	private BPLNode parent;

	public BPLNumberNode(int num, int lineNumber) {
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