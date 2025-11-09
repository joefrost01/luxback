package com.lbg.markets.luxback.exception;

/**
 * Exception thrown when uploaded file exceeds maximum size limit
 */
public class FileSizeExceededException extends RuntimeException {

    public FileSizeExceededException(String message) {
        super(message);
    }
}
