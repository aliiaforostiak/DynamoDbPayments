package com.sulf.dyndb.persistence.domain;

public record PaymentIntegrationEvent(
        String eventId,
        String eventType,
        String paymentId,
        String previousStatus,
        String newStatus,
        long paymentVersion,
        String occurredAt) {
}
