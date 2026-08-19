package com.slotforge.api.auth;

public class EmailAlreadyRegisteredException
        extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account cannot be created with that email");
    }
}
