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

import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.IDEMPOTENCY_NOT_EXISTS_CONDITION;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.IDEMPOTENCY_NOT_EXPIRES_CONDITION;

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
                .expression(IDEMPOTENCY_NOT_EXISTS_CONDITION + " OR " + IDEMPOTENCY_NOT_EXPIRES_CONDITION)
                .expressionNames(Map.of(
                        "#expiresAt",
                        "expiresAt"))
                .expressionValues(Map.of(
                        ":now",
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
                .expression("#status = :expectedStatus " +
                        "AND #version = :expectedVersion")
                .expressionNames(Map.of(
                        "#status",
                        "status",
                        "#version",
                        "version"))
                .expressionValues(
                        Map.of(
                                ":expectedStatus",
                                AttributeValue.builder()
                                        .s(expectedStatus.name())
                                        .build(),
                                ":expectedVersion",
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
