package it.eng.dome.revenue.engine.exception;

public class ExternalServiceException extends Exception {
    
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ExternalServiceException(String message) {
		super(message);
	}
}
