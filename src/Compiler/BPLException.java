package Compiler;

public class BPLException extends Exception {
	public BPLException() { 
		super(); 
	}

	public BPLException(String message) { 
		super(message + "."); 
	}

	public BPLException(String message, int lineNumber) { 
		super(message + " (Line " + lineNumber + ")."); 
	}
}