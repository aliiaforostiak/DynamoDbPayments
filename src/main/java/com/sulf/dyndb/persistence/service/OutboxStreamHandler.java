package com.sulf.dyndb.persistence.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.sulf.dyndb.persistence.domain.OutboxEventMapper;
import com.sulf.dyndb.persistence.domain.OutboxItem;
import com.sulf.dyndb.persistence.domain.PaymentIntegrationEvent;


import java.util.Map;

public class OutboxStreamHandler implements RequestHandler<DynamodbEvent, Void> {

    private final PaymentEventPublisher publisher;
    private final OutboxEventMapper mapper;

    public OutboxStreamHandler(PaymentEventPublisher publisher, OutboxEventMapper mapper) {
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            process(record);
        }

        return null;
    }

    private void process(DynamodbEvent.DynamodbStreamRecord record) {
        if (!"INSERT".equals(record.getEventName())) {
            return;
        }

        Map<String, AttributeValue> image = record.getDynamodb().getNewImage();
        if (image == null) {
            return;
        }

        AttributeValue entityType = image.get("entityType");
        if (entityType == null || !"OUTBOX".equals(entityType.getS())) {
            return;
        }

        OutboxItem outbox = mapOutbox(image);
        PaymentIntegrationEvent event = mapper.map(outbox);
        publisher.publish(event);
    }

    private OutboxItem mapOutbox(
            Map<String, AttributeValue> image
    ) {
        OutboxItem item = new OutboxItem();
        item.setEventId(image.get("eventId").getS());
        item.setEventType(image.get("eventType").getS());
        item.setPaymentId(image.get("paymentId").getS());
        item.setPreviousStatus(image.get("previousStatus").getS());
        item.setNewStatus(image.get("newStatus").getS());
        item.setPaymentVersion(Long.parseLong(image
                .get("paymentVersion")
                .getN()));
        item.setCreatedAt(image.get("createdAt").getS());
        return item;
    }
}
