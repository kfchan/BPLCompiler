package Compiler;

import java.io.*;
import java.util.*;

public class BPLVarNode extends BPLNode {
	private int lineNumber;
	private String id;
	private BPLNode parent;

	public BPLVarNode(String id, int lineNumber) {
		super("id", lineNumber);
		this.id = id;
		this.lineNumber = lineNumber;
	}

	@Override
	public String toString() {
		String rtn = "Line " + this.getLineNumber() + ": " + id + "\n";
		return rtn;
	}

}