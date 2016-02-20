package Compiler;

import java.io.*;
import java.util.*;

public class BPLNode {
	private int lineNumber;
	private String type;
	private ArrayList<BPLNode> children;
	private BPLNode parent;

	public BPLNode(String type, int lineNumber) {
		this.type = type;
		this.lineNumber = lineNumber;
		this.children = new ArrayList<BPLNode>();
	}

	public int getLineNumber() {
		return this.lineNumber;
	}

	public int setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
		return lineNumber;
	}

	public ArrayList<BPLNode> getChildren() {
		return this.children;
	}

	public BPLNode getChild(int i) {
		return this.children.get(i);
	}

	public void addChild(BPLNode child) {
		this.children.add(child);
	}

	public int getChildrenSize() {
		return this.children.size();
	}

	public String toString() {
		String rtn = "Line " + this.getLineNumber() + ": " + type + "\n";
		return rtn;
	}

}