package com.voting.resultservice.exception;

/**
 * Thrown when an election cannot be found for the given identifier.
 */
public class ElectionNotFoundException extends RuntimeException {

    public ElectionNotFoundException(String message) {
        super(message);
    }
}
