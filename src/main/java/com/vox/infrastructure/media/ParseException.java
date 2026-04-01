package com.vox.infrastructure.media;

public class ParseException extends Exception {
    
    public ParseException(String message) {
        super(message);
    }
    
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
