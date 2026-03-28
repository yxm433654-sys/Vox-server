package com.chatapp.exception;

public class TranscodeException extends Exception {
    
    public TranscodeException(String message) {
        super(message);
    }
    
    public TranscodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
