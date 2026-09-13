package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.*;

public interface PaymentTransactionRepository {

    void createPayment(PaymentItem payment, PaymentEventItem createdEvent, IdempotencyItem idempotency, long nowEpochSeconds);

    void changeStatus(PaymentItem payment, PaymentEventItem event, OutboxItem outbox, PaymentStatus expectedStatus, long version);

}
