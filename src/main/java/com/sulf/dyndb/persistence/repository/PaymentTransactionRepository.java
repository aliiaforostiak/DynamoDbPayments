package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.IdempotencyItem;
import com.sulf.dyndb.persistence.domain.PaymentEventItem;
import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.domain.PaymentStatus;

public interface PaymentTransactionRepository {

    void createPayment(PaymentItem payment, PaymentEventItem createdEvent, IdempotencyItem idempotency, long nowEpochSeconds);

    void changeStatus(PaymentItem payment, PaymentEventItem event, PaymentStatus expectedStatus, long version);

}
