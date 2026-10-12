package com.tommap.springaiapp.order;

/** A business rule refused the request; the message is written for the model to relay to the customer. */
public class OrderRuleException extends RuntimeException {

    public OrderRuleException(String message) {
        super(message);
    }
}
