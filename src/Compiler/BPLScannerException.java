package Compiler;

public class BPLScannerException extends Exception {
	public BPLScannerException() { 
		super(); 
	}

	public BPLScannerException(String message) { 
		super(message); 
	}

	public BPLScannerException(String message, int lineNumber) { 
		super(message + " (Line " + lineNumber + ")."); 
	}
}