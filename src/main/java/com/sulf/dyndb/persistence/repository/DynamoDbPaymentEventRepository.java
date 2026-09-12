package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.PaymentEventItem;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.List;

import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.EVENT_SORT_KEY_PREFIX;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.paymentPartitionKey;

@Repository
public class DynamoDbPaymentEventRepository implements PaymentEventRepository {

    private final DynamoDbTable<PaymentEventItem> eventTable;

    public DynamoDbPaymentEventRepository(
            DynamoDbTable<PaymentEventItem> eventTable
    ) {
        this.eventTable = eventTable;
    }

    @Override
    public void save(PaymentEventItem event) {
        eventTable.putItem(event);
    }

    @Override
    public List<PaymentEventItem> findAllByPaymentId(
            String paymentId
    ) {
        String partitionKey = paymentPartitionKey(paymentId);

        QueryConditional condition =
                QueryConditional.sortBeginsWith(
                        Key.builder()
                                .partitionValue(partitionKey)
                                .sortValue(EVENT_SORT_KEY_PREFIX)
                                .build()
                );

        List<PaymentEventItem> result = new ArrayList<>();

        eventTable.query(condition)
                .items()
                .forEach(result::add);

        return result;
    }
}
