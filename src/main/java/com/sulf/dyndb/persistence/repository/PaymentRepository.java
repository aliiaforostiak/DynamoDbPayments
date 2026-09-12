package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.domain.PaymentPage;
import com.sulf.dyndb.persistence.domain.PaymentStatus;
import com.sulf.dyndb.persistence.domain.SortDirection;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentRepository {
    void save(PaymentItem payment);

    List<PaymentItem> findAllByCustomerId(String customerId);

    PaymentPage findPageByCustomerId(
            String customerId,
            int limit,
            Map<String, AttributeValue> exclusiveStartKey,
            SortDirection sortDirection
    );

    Optional<PaymentItem> findByPaymentId(String paymentId);

    List<PaymentItem> findAllByCustomerIdAndPeriod(String customerId, Instant fromInclusive, Instant toExclusive);

    Optional<PaymentItem> findByPrimaryKey(
            String pk,
            String sk
    );

    List<PaymentItem> findStaleByStatus(
            PaymentStatus status,
            Instant olderThan,
            int limit
    );
}
