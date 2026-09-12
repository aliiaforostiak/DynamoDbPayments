package com.sulf.dyndb.persistence.service;

import com.sulf.dyndb.persistence.domain.PaymentEventItem;
import com.sulf.dyndb.persistence.repository.PaymentEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.eventSortKey;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.paymentPartitionKey;

@Service
public class PaymentEventService {

    private final PaymentEventRepository repository;

    public PaymentEventService(PaymentEventRepository repository) {
        this.repository = repository;
    }

    public PaymentEventItem addEvent(String paymentId, String type){
        String eventId = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        PaymentEventItem event = new PaymentEventItem();
        event.setPk(paymentPartitionKey(paymentId));
        event.setSk(eventSortKey(createdAt, eventId));

        event.setEventId(eventId);
        event.setPaymentId(paymentId);
        event.setType(type);
        event.setCreatedAt(createdAt);

        repository.save(event);

        return event;
    }

    public List<PaymentEventItem> findAll(
            String paymentId
    ) {
        return repository.findAllByPaymentId(paymentId);
    }
}
