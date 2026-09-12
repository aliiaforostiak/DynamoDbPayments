package com.sulf.dyndb.persistence.domain;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

public record PaymentPage(
        List<PaymentItem> items,
        Map<String, AttributeValue> lastEvaluatedKey
) {
}
