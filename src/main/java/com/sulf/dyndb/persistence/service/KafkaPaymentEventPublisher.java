package com.sulf.dyndb.persistence.service;

import com.sulf.dyndb.persistence.domain.PaymentIntegrationEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private static final String TOPIC = "payment-events";
    private final KafkaTemplate<String, String> template;
    private final ObjectMapper objectMapper;

    public KafkaPaymentEventPublisher(
            KafkaTemplate<String, String> template,
            ObjectMapper objectMapper
    ) {
        this.template = template;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(PaymentIntegrationEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize payment event",
                    exception);
        }
        ProducerRecord<String, String> record = new ProducerRecord<>(
                TOPIC,
                event.paymentId(),
                payload
        );
        try {
            template.send(record).get();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to publish payment event",
                    exception
            );
        }
    }
}
