package com.sulf.dyndb.persistence.domain;

import java.time.Instant;

public final class DynamoDbSchema {

    public static final String PAYMENT_ID_INDEX = "payment-id-index";
    public static final String IDEMPOTENCY_REQUEST_SORT_KEY = "REQUEST";
    public static final String EVENT_SORT_KEY_PREFIX = "EVENT#";
    public static final String IDEMPOTENCY_NOT_EXISTS_CONDITION = "attribute_not_exists(pk)";
    public static final String IDEMPOTENCY_NOT_EXPIRES_CONDITION = "#expiresAt <= :now";

    public static final String CUSTOMER_KEY_PREFIX = "CUSTOMER#";
    public static final String PAYMENT_KEY_PREFIX = "PAYMENT#";
    private static final String IDEMPOTENCY_KEY_PREFIX = "IDEMPOTENCY#";
    public static final String KEY_PART_SEPARATOR = "#";

    private DynamoDbSchema() {
    }

    public static String customerPartitionKey(String customerId) {
        return CUSTOMER_KEY_PREFIX + customerId;
    }

    public static String paymentPartitionKey(String paymentId) {
        return PAYMENT_KEY_PREFIX + paymentId;
    }

    public static String paymentSortKey(String createdAt, String paymentId) {
        return paymentSortKeyPrefix(createdAt) + KEY_PART_SEPARATOR + paymentId;
    }

    public static String paymentSortKeyPrefix(Instant createdAt) {
        return paymentSortKeyPrefix(createdAt.toString());
    }

    public static String paymentSortKeyPrefix(String createdAt) {
        return PAYMENT_KEY_PREFIX + createdAt;
    }

    public static String eventSortKey(String createdAt, String eventId) {
        return EVENT_SORT_KEY_PREFIX + createdAt + KEY_PART_SEPARATOR + eventId;
    }

    public static String idempotencyPartitionKey(String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
    }
}
