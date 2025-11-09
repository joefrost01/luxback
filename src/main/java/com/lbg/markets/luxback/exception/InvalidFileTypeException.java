package com.lbg.markets.luxback.exception;

/**
 * Exception thrown when uploaded file type is not allowed
 */
public class InvalidFileTypeException extends RuntimeException {

    public InvalidFileTypeException(String message) {
        super(message);
    }
}
