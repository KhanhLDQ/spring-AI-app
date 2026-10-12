package com.tommap.springaiapp.auth;

/** Thrown by admin tools for other roles; the message is relayed to the user by the model. */
public class AdminOnlyException extends RuntimeException {

    public AdminOnlyException(String action) {
        super("Only the shop administrator can " + action + ". The current user does not have that permission.");
    }
}
