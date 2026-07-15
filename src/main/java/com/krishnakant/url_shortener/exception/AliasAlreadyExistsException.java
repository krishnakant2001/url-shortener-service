package com.krishnakant.url_shortener.exception;

public class AliasAlreadyExistsException extends RuntimeException {
    public AliasAlreadyExistsException(String alias) {
        super("Alias already taken: " + alias);
    }
}
