package Compiler;

public class BPLTypeCheckerException extends BPLException {
	public BPLTypeCheckerException() { 
		super(); 
	}

	public BPLTypeCheckerException(String message, int lineNumber) { 
		super(message + " (Line " + lineNumber + ")."); 
	}
}