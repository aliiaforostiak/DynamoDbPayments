package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.IdempotencyItem;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.Instant;
import java.util.Optional;

import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.IDEMPOTENCY_REQUEST_SORT_KEY;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.idempotencyPartitionKey;

@Repository
public class DynamoDbIdempotencyRepository implements IdempotencyRepository {

    private final DynamoDbTable<IdempotencyItem> table;

    public DynamoDbIdempotencyRepository(
            DynamoDbTable<IdempotencyItem> table
    ) {
        this.table = table;
    }

    @Override
    public Optional<IdempotencyItem> findByKey(String idempotencyKey) {
        IdempotencyItem item = table
                .getItem(request ->
                        request
                                .key(key ->
                                        key
                                                .partitionValue(idempotencyPartitionKey(idempotencyKey))
                                                .sortValue(IDEMPOTENCY_REQUEST_SORT_KEY))
                                .consistentRead(true)
                );

        if (item == null) {
            return Optional.empty();
        }

        long now = Instant.now().getEpochSecond();
        if (item.getExpiresAt() != null && item.getExpiresAt() <= now) {
            return Optional.empty();
        }

        return Optional.of(item);
    }
}
