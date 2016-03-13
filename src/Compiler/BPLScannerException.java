package Compiler;

public class BPLScannerException extends BPLException {
	public BPLScannerException() { 
		super(); 
	}

	public BPLScannerException(String message, int lineNumber) { 
		super(message + " (Line " + lineNumber + ")."); 
	}
}