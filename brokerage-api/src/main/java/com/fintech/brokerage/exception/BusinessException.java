package com.fintech.brokerage.exception;

//Custom business exception class (add to your exception package)
public class BusinessException extends RuntimeException {

	private static final long serialVersionUID = -318642604612437509L;

	public BusinessException(String message) {
        super(message);
    }
    
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}