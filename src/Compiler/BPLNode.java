package Compiler;

import java.io.*;
import java.util.*;

public class BPLNode {
	private final String type;

	private int lineNumber;
	private ArrayList<BPLNode> children;
	private BPLNode parent;
	private BPLNode declaration;
	private String name;

	public BPLNode(String type, int lineNumber) {
		this.type = type;
		this.lineNumber = lineNumber;
		this.children = new ArrayList<BPLNode>();
		this.declaration = null;
		this.name = null;
	}

	public void setName(String n) {
		this.name = n;
	}

	public String getName() {
		return this.name;
	}

	public String getType() {
		return this.type;
	}

	public boolean isType(String t) {
		return (this.type.equals(t));
	}

	public int getLineNumber() {
		return this.lineNumber;
	}

	public int setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
		return lineNumber;
	}

	public void setDeclaration(BPLNode dec) {
		this.declaration = dec;
	}

	public BPLNode getDeclaration() {
		return this.declaration;
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

	public void setChild(int index, BPLNode child) {
		this.children.set(index, child);
	}

	public int getChildrenSize() {
		return this.children.size();
	}

	public boolean isChildrenSize(int n) {
		return (this.children.size() == n);
	}

	public String toString() {
		String rtn = "Line " + this.getLineNumber() + ": " + type + "\n";
		return rtn;
	}

}