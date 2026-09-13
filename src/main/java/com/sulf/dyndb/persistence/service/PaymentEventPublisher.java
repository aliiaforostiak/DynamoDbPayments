package com.sulf.dyndb.persistence.service;

import com.sulf.dyndb.persistence.domain.PaymentIntegrationEvent;

public interface PaymentEventPublisher {
    void publish(PaymentIntegrationEvent event);
}
