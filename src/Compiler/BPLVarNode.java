package Compiler;

import java.io.*;
import java.util.*;

public class BPLVarNode extends BPLNode {
	private int lineNumber;
	private String id;
	private BPLNode parent;

	public BPLVarNode(String id, int lineNumber) {
		super("ID", lineNumber);
		this.id = id;
		this.lineNumber = lineNumber;
	}

	public String getID() {
		return this.id;
	}

	@Override
	public String toString() {
		String rtn = "Line " + this.getLineNumber() + ": ID " + id + "\n";
		return rtn;
	}

}