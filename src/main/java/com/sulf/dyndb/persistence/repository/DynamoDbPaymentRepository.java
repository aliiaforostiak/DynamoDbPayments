package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.domain.PaymentPage;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.*;

@Repository
public class DynamoDbPaymentRepository implements PaymentRepository {

    private final DynamoDbTable<PaymentItem> paymentTable;

    public DynamoDbPaymentRepository(
            DynamoDbTable<PaymentItem> paymentTable
    ) {
        this.paymentTable = paymentTable;
    }

    @Override
    public void save(PaymentItem payment) {
        paymentTable.putItem(payment);
    }

    @Override
    public List<PaymentItem> findAllByCustomerId(
            String customerId
    ) {
        String partitionKey = customerPartitionKey(customerId);

        QueryConditional condition = QueryConditional.keyEqualTo(
                key -> key.partitionValue(partitionKey));

        List<PaymentItem> result = new ArrayList<>();

        paymentTable.query(condition)
                .items()
                .forEach(result::add);

        return result;
    }

    @Override
    public PaymentPage findAllByCustomerIdPaginatedFromOld(String customerId, int limit, Map<String, AttributeValue> exclusiveStartKey) {
        String partitionKey = customerPartitionKey(customerId);

        QueryConditional condition = QueryConditional.keyEqualTo(
                key -> key.partitionValue(partitionKey));

        QueryEnhancedRequest.Builder requestBuilder =
                QueryEnhancedRequest.builder()
                        .queryConditional(condition)
                        .limit(limit);

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        PageIterable<PaymentItem> pages = paymentTable.query(requestBuilder.build());
        Page<PaymentItem> page = pages.stream().findFirst().orElseThrow();

        return new PaymentPage(page.items(), page.lastEvaluatedKey());
    }

    @Override
    public PaymentPage findAllByCustomerIdPaginatedFromNew(String customerId, int limit, Map<String, AttributeValue> exclusiveStartKey) {
        String partitionKey = customerPartitionKey(customerId);

        QueryConditional condition = QueryConditional.keyEqualTo(
                key -> key.partitionValue(partitionKey));

        QueryEnhancedRequest.Builder requestBuilder =
                QueryEnhancedRequest.builder()
                        .queryConditional(condition)
                        .scanIndexForward(false)
                        .limit(limit);

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        PageIterable<PaymentItem> pages = paymentTable.query(requestBuilder.build());
        Page<PaymentItem> page = pages.stream().findFirst().orElseThrow();

        return new PaymentPage(page.items(), page.lastEvaluatedKey());
    }

    @Override
    public List<PaymentItem> findAllByCustomerIdAndPeriod(
            String customerId,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        String partitionKey = customerPartitionKey(customerId);
        String fromSortKey = paymentSortKeyPrefix(fromInclusive);
        String toSortKey = paymentSortKeyPrefix(toExclusive);

        QueryConditional condition = QueryConditional.sortBetween(
                Key.builder()
                        .partitionValue(partitionKey)
                        .sortValue(fromSortKey)
                        .build(),

                Key.builder()
                        .partitionValue(partitionKey)
                        .sortValue(toSortKey)
                        .build()
        );

        List<PaymentItem> result = new ArrayList<>();

        paymentTable.query(condition)
                .items()
                .forEach(result::add);

        return result;
    }

    @Override
    public Optional<PaymentItem> findByPaymentId(
            String paymentId
    ) {
        String indexKey = paymentPartitionKey(paymentId);

        DynamoDbIndex<PaymentItem> paymentIdIndex = paymentTable.index(PAYMENT_ID_INDEX);

        QueryConditional condition = QueryConditional.keyEqualTo(
                key -> key.partitionValue(indexKey));

        return paymentIdIndex
                .query(condition)
                .stream()
                .flatMap(page ->
                        page.items().stream()
                )
                .findFirst();
    }

    @Override
    public Optional<PaymentItem> findByPrimaryKey(
            String pk,
            String sk
    ) {
        PaymentItem item = paymentTable.getItem(request ->
                request
                        .key(key ->
                                key
                                        .partitionValue(pk)
                                        .sortValue(sk)
                        )
                        .consistentRead(true)
        );

        return Optional.ofNullable(item);
    }
}
