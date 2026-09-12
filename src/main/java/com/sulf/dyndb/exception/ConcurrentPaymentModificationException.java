package com.sulf.dyndb.exception;

public class ConcurrentPaymentModificationException extends RuntimeException {
    public ConcurrentPaymentModificationException(String paymentId,
                                                  long expectedVersion) {
        super("Payment "
                + paymentId
                + " was modified concurrently. "
                + "Expected version: "
                + expectedVersion);
    }
}
