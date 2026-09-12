package com.sulf.dyndb.persistence.service;

import com.sulf.dyndb.persistence.domain.PaymentEventItem;
import com.sulf.dyndb.persistence.repository.PaymentEventRepository;
import com.sulf.dyndb.persistence.repository.PaymentRepository;
import com.sulf.dyndb.exception.PaymentNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.eventSortKey;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.paymentPartitionKey;

@Service
public class PaymentEventService {

    private final PaymentEventRepository repository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public PaymentEventService(
            PaymentEventRepository repository,
            PaymentRepository paymentRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    public PaymentEventItem addEvent(String paymentId, String type){
        if (paymentRepository.findByPaymentId(paymentId).isEmpty()) {
            throw new PaymentNotFoundException(paymentId);
        }
        String eventId = UUID.randomUUID().toString();
        String createdAt = clock.instant().toString();

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
