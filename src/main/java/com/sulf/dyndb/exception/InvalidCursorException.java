package com.sulf.dyndb.exception;

public class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String message) {
        super("Invalid pagination cursor");
    }
}
