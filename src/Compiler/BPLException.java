package Compiler;

public class BPLException extends Exception {
	public BPLException() { 
		super(); 
	}

	public BPLException(String message) { 
		super(message + "."); 
	}
}