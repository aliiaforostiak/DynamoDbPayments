package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.domain.PaymentPage;
import com.sulf.dyndb.persistence.domain.PaymentStatus;
import com.sulf.dyndb.persistence.domain.SortDirection;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
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
    public PaymentPage findPageByCustomerId(
            String customerId,
            int limit,
            Map<String, AttributeValue> exclusiveStartKey,
            SortDirection sortDirection
    ) {
        QueryEnhancedRequest.Builder request =
                QueryEnhancedRequest.builder()
                        .queryConditional(customerQuery(customerId))
                        .limit(limit)
                        .scanIndexForward(sortDirection.scanIndexForward());

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            request.exclusiveStartKey(
                    exclusiveStartKey
            );
        }

        Page<PaymentItem> page = paymentTable
                .query(request.build())
                .stream()
                .findFirst()
                .orElseThrow();

        return new PaymentPage(
                page.items(),
                page.lastEvaluatedKey()
        );
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

    @Override
    public List<PaymentItem> findStaleByStatus(PaymentStatus status, Instant olderThan, int limit) {
        DynamoDbIndex<PaymentItem> index = paymentTable.index(STATUS_UPDATED_AT_INDEX);
        String partitionKey = statusPartitionKey(status);

        QueryConditional condition =
                QueryConditional.sortLessThan(
                        key ->
                                key
                                        .partitionValue(
                                                partitionKey
                                        )
                                        .sortValue(
                                                olderThan.toString()
                                        )
                );

        List<PaymentItem> result = new ArrayList<>();

        index.query(request ->
                        request
                                .queryConditional(condition)
                                .limit(limit)
                )
                .stream()
                .flatMap(page ->
                        page.items().stream()
                )
                .limit(limit)
                .forEach(result::add);

        return result;
    }

    private QueryConditional customerQuery(String customerId) {
        return QueryConditional.keyEqualTo(
                key -> key.partitionValue(customerPartitionKey(customerId))
        );
    }
}
