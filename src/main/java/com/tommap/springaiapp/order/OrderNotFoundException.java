package com.tommap.springaiapp.order;

/** Also thrown for orders owned by someone else, so ids of other users are not revealed. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long id) {
        super("Order #" + id + " was not found.");
    }
}
