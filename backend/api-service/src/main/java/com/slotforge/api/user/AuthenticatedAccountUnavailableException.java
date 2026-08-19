package com.slotforge.api.user;

public class AuthenticatedAccountUnavailableException
        extends RuntimeException {

    public AuthenticatedAccountUnavailableException() {
        super("The authenticated account is unavailable");
    }
}
