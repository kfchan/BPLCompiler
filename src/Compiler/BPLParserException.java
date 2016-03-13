package Compiler;

public class BPLParserException extends BPLException {
	public BPLParserException() { 
		super(); 
	}

	public BPLParserException(String message) { 
		super(message); 
	}	

	public BPLParserException(String message, int lineNumber) { 
		super(message + " (Line " + lineNumber + ")"); 
	}
}