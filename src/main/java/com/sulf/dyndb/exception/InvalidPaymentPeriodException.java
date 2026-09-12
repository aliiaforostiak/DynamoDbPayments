package com.sulf.dyndb.exception;

public class InvalidPaymentPeriodException extends RuntimeException {

    public InvalidPaymentPeriodException() {
        super("Both from and to must be set, and from must be earlier than to");
    }
}
