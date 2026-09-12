package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.domain.PaymentPage;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentRepository {
    void save(PaymentItem payment);

    List<PaymentItem> findAllByCustomerId(String customerId);

    PaymentPage findAllByCustomerIdPaginatedFromNew(String customerId, int limit, Map<String, AttributeValue> exclusiveStartKey);

    PaymentPage findAllByCustomerIdPaginatedFromOld(String customerId, int limit, Map<String, AttributeValue> exclusiveStartKey);

    PaymentPage findPageByCustomerId(String customerId, int limit, Map<String, AttributeValue> exclusiveStartKey);

    Optional<PaymentItem> findByPaymentId(String paymentId);

    List<PaymentItem> findAllByCustomerIdAndPeriod(String customerId, Instant fromInclusive, Instant toExclusive);

    Optional<PaymentItem> findByPrimaryKey(
            String pk,
            String sk
    );
}
