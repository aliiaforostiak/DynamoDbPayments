package com.sulf.dyndb.persistence.domain;

import java.time.Instant;

public final class DynamoDbSchema {

    public static final String PAYMENT_ID_INDEX = "payment-id-index";
    public static final String STATUS_UPDATED_AT_INDEX = "status-updated-at-index";

    public static final String PARTITION_KEY_ATTRIBUTE = "pk";
    public static final String SORT_KEY_ATTRIBUTE = "sk";
    public static final String STATUS_ATTRIBUTE = "status";
    public static final String VERSION_ATTRIBUTE = "version";
    public static final String EXPIRES_AT_ATTRIBUTE = "expiresAt";
    public static final String IDEMPOTENCY_REQUEST_SORT_KEY = "REQUEST";
    public static final String EVENT_SORT_KEY_PREFIX = "EVENT#";
    public static final String IDEMPOTENCY_NOT_EXISTS_CONDITION =
            "attribute_not_exists(" + PARTITION_KEY_ATTRIBUTE + ")";
    public static final String IDEMPOTENCY_IS_EXPIRED_CONDITION = "#expiresAt <= :now";
    public static final String IDEMPOTENCY_WRITE_CONDITION =
            IDEMPOTENCY_NOT_EXISTS_CONDITION + " OR " + IDEMPOTENCY_IS_EXPIRED_CONDITION;
    public static final String IDEMPOTENCY_EXPIRES_AT_ATTRIBUTE_NAME = "#expiresAt";
    public static final String IDEMPOTENCY_NOW_VALUE_NAME = ":now";
    public static final String STATUS_ATTRIBUTE_NAME = "#status";
    public static final String VERSION_ATTRIBUTE_NAME = "#version";
    public static final String EXPECTED_STATUS_VALUE_NAME = ":expectedStatus";
    public static final String EXPECTED_VERSION_VALUE_NAME = ":expectedVersion";
    public static final String STATUS_VERSION_CONDITION =
            STATUS_ATTRIBUTE_NAME + " = " + EXPECTED_STATUS_VALUE_NAME
                    + " AND " + VERSION_ATTRIBUTE_NAME + " = " + EXPECTED_VERSION_VALUE_NAME;

    public static final String CUSTOMER_KEY_PREFIX = "CUSTOMER#";
    public static final String STATUS_KEY_PREFIX = "STATUS#";
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

    public static String statusPartitionKey(PaymentStatus status) {
        return STATUS_KEY_PREFIX + status.name();
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
