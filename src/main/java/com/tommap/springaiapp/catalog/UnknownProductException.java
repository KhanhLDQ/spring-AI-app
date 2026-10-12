package com.tommap.springaiapp.catalog;

/** The message is read by the model, so it names the valid choices. */
public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String message) {
        super(message);
    }
}
