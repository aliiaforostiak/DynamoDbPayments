package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.IdempotencyItem;
import com.sulf.dyndb.persistence.domain.PaymentEventItem;
import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.domain.PaymentStatus;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactUpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.EXPIRES_AT_ATTRIBUTE;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.IDEMPOTENCY_EXPIRES_AT_ATTRIBUTE_NAME;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.IDEMPOTENCY_NOW_VALUE_NAME;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.IDEMPOTENCY_WRITE_CONDITION;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.EXPECTED_STATUS_VALUE_NAME;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.EXPECTED_VERSION_VALUE_NAME;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.STATUS_ATTRIBUTE;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.STATUS_ATTRIBUTE_NAME;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.STATUS_VERSION_CONDITION;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.VERSION_ATTRIBUTE;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.VERSION_ATTRIBUTE_NAME;

@Repository
public class DynamoDbPaymentTransactionalRepository implements PaymentTransactionRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<PaymentItem> paymentTable;
    private final DynamoDbTable<PaymentEventItem> eventTable;
    private final DynamoDbTable<IdempotencyItem> idempotencyTable;

    public DynamoDbPaymentTransactionalRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbTable<PaymentItem> paymentTable, DynamoDbTable<PaymentEventItem> eventTable, DynamoDbTable<IdempotencyItem> idempotencyTable) {
        this.enhancedClient = enhancedClient;
        this.paymentTable = paymentTable;
        this.eventTable = eventTable;
        this.idempotencyTable = idempotencyTable;
    }

    @Override
    public void createPayment(PaymentItem payment, PaymentEventItem createdEvent, IdempotencyItem idempotency, long nowEpochSeconds) {
        Expression idempotencyDoesNotExist = Expression
                .builder()
                .expression(IDEMPOTENCY_WRITE_CONDITION)
                .expressionNames(Map.of(
                        IDEMPOTENCY_EXPIRES_AT_ATTRIBUTE_NAME,
                        EXPIRES_AT_ATTRIBUTE))
                .expressionValues(Map.of(
                        IDEMPOTENCY_NOW_VALUE_NAME,
                        AttributeValue.builder()
                                .n(Long.toString(nowEpochSeconds))
                                .build()
                ))
                .build();

        TransactPutItemEnhancedRequest<IdempotencyItem> idempotencyRequest =
                TransactPutItemEnhancedRequest
                        .builder(IdempotencyItem.class)
                        .item(idempotency)
                        .conditionExpression(idempotencyDoesNotExist)
                        .build();

        enhancedClient.transactWriteItems(transaction ->
                transaction
                        .addPutItem(
                                idempotencyTable,
                                idempotencyRequest
                        )
                        .addPutItem(
                                paymentTable,
                                payment
                        )
                        .addPutItem(
                                eventTable,
                                createdEvent
                        )
        );

    }

    @Override
    public void changeStatus(PaymentItem payment, PaymentEventItem event, PaymentStatus expectedStatus, long expectedVersion) {
        Expression statusCondition = Expression.builder()
                .expression(STATUS_VERSION_CONDITION)
                .expressionNames(Map.of(
                        STATUS_ATTRIBUTE_NAME,
                        STATUS_ATTRIBUTE,
                        VERSION_ATTRIBUTE_NAME,
                        VERSION_ATTRIBUTE))
                .expressionValues(
                        Map.of(
                                EXPECTED_STATUS_VALUE_NAME,
                                AttributeValue.builder()
                                        .s(expectedStatus.name())
                                        .build(),
                                EXPECTED_VERSION_VALUE_NAME,
                                AttributeValue.builder()
                                        .n(Long.toString(expectedVersion))
                                        .build()
                        )
                )
                .build();

        TransactUpdateItemEnhancedRequest<PaymentItem> paymentUpdateRequest =
                TransactUpdateItemEnhancedRequest.builder(PaymentItem.class)
                        .item(payment)
                        .conditionExpression(statusCondition)
                        .build();

        enhancedClient.transactWriteItems(transaction ->
                transaction
                        .addUpdateItem(
                                paymentTable,
                                paymentUpdateRequest
                        )
                        .addPutItem(
                                eventTable,
                                event
                        )
        );

    }
}
