package com.sulf.dyndb.persistence.service;

import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Clock;
import java.util.List;

@Service
public class ReconciliationService {

    private static final int DEFAULT_RECONCILIATION_BATCH_SIZE = 100;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public ReconciliationService(
            PaymentRepository paymentRepository,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    public List<PaymentItem> findDuePayments() {
        return paymentRepository
                .findDueForReconciliation(
                        clock.instant(),
                        DEFAULT_RECONCILIATION_BATCH_SIZE
                );
    }
}
