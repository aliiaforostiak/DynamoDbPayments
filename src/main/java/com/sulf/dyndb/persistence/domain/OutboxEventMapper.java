package com.sulf.dyndb.persistence.domain;

public class OutboxEventMapper {
    public PaymentIntegrationEvent map(
            OutboxItem item
    ) {
        return new PaymentIntegrationEvent(
                item.getEventId(),
                item.getEventType(),
                item.getPaymentId(),
                item.getPreviousStatus(),
                item.getNewStatus(),
                item.getPaymentVersion(),
                item.getCreatedAt()
        );
    }
}
