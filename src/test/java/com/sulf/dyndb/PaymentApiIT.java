package com.sulf.dyndb;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sulf.dyndb.persistence.controller.CreatePaymentRequest;
import com.sulf.dyndb.persistence.domain.PaymentEventItem;
import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.domain.PaymentStatus;
import com.sulf.dyndb.persistence.domain.OutboxItem;
import com.sulf.dyndb.persistence.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.EXPIRES_AT_ATTRIBUTE;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.IDEMPOTENCY_REQUEST_SORT_KEY;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.PARTITION_KEY_ATTRIBUTE;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.SORT_KEY_ATTRIBUTE;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.eventSortKey;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.idempotencyPartitionKey;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.paymentPartitionKey;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.paymentEventType;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.outboxPartitionKey;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.OUTBOX_ENTITY_TYPE;
import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.OUTBOX_EVENT_SORT_KEY;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaymentApiIT {

    private static final String TABLE_NAME = "payment-history-it";

    @Container
    private static final GenericContainer<?> DYNAMO_DB = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:latest")
    )
            .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
            .withExposedPorts(8000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private DynamoDbTable<OutboxItem> outboxTable;

    @DynamicPropertySource
    static void dynamoDbProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint", () -> "http://" + DYNAMO_DB.getHost()
                + ":" + DYNAMO_DB.getMappedPort(8000));
        registry.add("aws.dynamodb.table-name", () -> TABLE_NAME);
    }

    @Test
    void createsPaymentAndPersistsCreatedEvent() throws Exception {
        PaymentItem payment = createPayment("customer-" + UUID.randomUUID(), "key-" + UUID.randomUUID());

        mockMvc.perform(get("/payments/{paymentId}", payment.getPaymentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getPaymentId()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(post("/payments/{paymentId}/events", payment.getPaymentId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"type\":\"NOTIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getPaymentId()))
                .andExpect(jsonPath("$.type").value("NOTIFIED"));

        List<PaymentEventItem> events = events(payment.getPaymentId());
        assertThat(events)
                .hasSize(2)
                .extracting(PaymentEventItem::getType)
                .containsExactly("CREATED", "NOTIFIED");
        assertThat(events).allSatisfy(event ->
                    assertThat(event.getPk()).isEqualTo(paymentPartitionKey(payment.getPaymentId()))
        );
    }

    @Test
    void repeatsIdempotentRequestButRejectsDifferentPayload() throws Exception {
        String customerId = "customer-" + UUID.randomUUID();
        String key = "key-" + UUID.randomUUID();
        CreatePaymentRequest request = new CreatePaymentRequest(customerId, new BigDecimal("10.00"), "rub");

        PaymentItem first = createPayment(request, key);
        PaymentItem repeated = createPayment(request, key);

        assertThat(repeated.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(paymentsFor(customerId)).extracting(PaymentItem::getPaymentId)
                .containsExactly(first.getPaymentId());

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", key)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePaymentRequest(customerId, new BigDecimal("11.00"), "RUB")
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void readsPaymentsByCustomerAndPeriod() throws Exception {
        String customerId = "customer-" + UUID.randomUUID();
        PaymentItem payment = createPayment(customerId, "key-" + UUID.randomUUID());

        assertThat(paymentsFor(customerId)).extracting(PaymentItem::getPaymentId)
                .contains(payment.getPaymentId());

        String from = Instant.parse(payment.getCreatedAt()).minusSeconds(1).toString();
        String to = Instant.parse(payment.getCreatedAt()).plusSeconds(1).toString();
        mockMvc.perform(get("/customers/{customerId}/payments", customerId)
                        .queryParam("from", from)
                        .queryParam("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value(payment.getPaymentId()));
    }

    @Test
    void paginatesCustomerPaymentsWithoutDuplicates() throws Exception {
        String customerId = "customer-" + UUID.randomUUID();
        createPayment(customerId, "key-" + UUID.randomUUID());
        createPayment(customerId, "key-" + UUID.randomUUID());
        createPayment(customerId, "key-" + UUID.randomUUID());

        String firstPage = mockMvc.perform(get("/customers/{customerId}/page", customerId)
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String cursor = objectMapper.readTree(firstPage).get("nextCursor").asString();

        mockMvc.perform(get("/customers/{customerId}/page", customerId)
                        .queryParam("limit", "2")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void supportsAscendingAndDescendingPaymentSortOrder() throws Exception {
        String customerId = "customer-" + UUID.randomUUID();
        PaymentItem first = createPayment(customerId, "key-" + UUID.randomUUID());
        PaymentItem second = createPayment(customerId, "key-" + UUID.randomUUID());

        mockMvc.perform(get("/customers/{customerId}/page", customerId)
                        .queryParam("limit", "10")
                        .queryParam("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].paymentId").value(first.getPaymentId()))
                .andExpect(jsonPath("$.items[1].paymentId").value(second.getPaymentId()));

        mockMvc.perform(get("/customers/{customerId}/page", customerId)
                        .queryParam("limit", "10")
                        .queryParam("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].paymentId").value(second.getPaymentId()))
                .andExpect(jsonPath("$.items[1].paymentId").value(first.getPaymentId()));
    }

    @Test
    void rejectsInvalidPeriodAndEventsForMissingPayment() throws Exception {
        mockMvc.perform(get("/customers/{customerId}/payments", UUID.randomUUID())
                        .queryParam("from", Instant.now().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_PERIOD"));

        mockMvc.perform(post("/payments/{paymentId}/events", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{\"type\":\"NOTIFIED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void changesStateAndWritesEvents() throws Exception {
        PaymentItem payment = createPayment("customer-" + UUID.randomUUID(), "key-" + UUID.randomUUID());

        mockMvc.perform(post("/payments/{paymentId}/capture", payment.getPaymentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/payments/{paymentId}/authorize", payment.getPaymentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/payments/{paymentId}/refund", payment.getPaymentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.version").value(3));

        assertThat(events(payment.getPaymentId())).extracting(PaymentEventItem::getType)
                .containsExactly("CREATED", "AUTHORIZED", "CAPTURED", "REFUNDED");

        List<OutboxItem> outboxItems = outboxTable.scan().items().stream()
                .filter(item -> payment.getPaymentId().equals(item.getPaymentId()))
                .filter(item -> item.getEventType() != null)
                .toList();
        assertThat(outboxItems).extracting(OutboxItem::getEventType)
                .containsExactlyInAnyOrder(
                        paymentEventType(PaymentStatus.AUTHORIZED),
                        paymentEventType(PaymentStatus.CAPTURED),
                        paymentEventType(PaymentStatus.REFUNDED)
                );
        assertThat(outboxItems).allSatisfy(item -> assertThat(item.getExpiresAt()).isPositive());
    }

    @Test
    void returnsDomainErrorsForMissingPaymentAndInvalidTransition() throws Exception {
        mockMvc.perform(get("/payments/{paymentId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        PaymentItem payment = createPayment("customer-" + UUID.randomUUID(), "key-" + UUID.randomUUID());
        mockMvc.perform(post("/payments/{paymentId}/refund", payment.getPaymentId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATE"));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "key-" + UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"customerId":"", "amount":0, "currency":"RU"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void reusesAnExpiredIdempotencyKey() throws Exception {
        String key = "key-" + UUID.randomUUID();
        PaymentItem first = createPayment("customer-" + UUID.randomUUID(), key);

        dynamoDbClient.updateItem(request -> request
                .tableName(TABLE_NAME)
                .key(Map.of(
                        PARTITION_KEY_ATTRIBUTE, AttributeValue.fromS(idempotencyPartitionKey(key)),
                        SORT_KEY_ATTRIBUTE, AttributeValue.fromS(IDEMPOTENCY_REQUEST_SORT_KEY)
                ))
                .updateExpression("SET #expiresAt = :expiresAt")
                .expressionAttributeNames(Map.of("#expiresAt", EXPIRES_AT_ATTRIBUTE))
                .expressionAttributeValues(Map.of(":expiresAt", AttributeValue.fromN("0"))));

        PaymentItem replacement = createPayment("customer-" + UUID.randomUUID(), key);

        assertThat(replacement.getPaymentId()).isNotEqualTo(first.getPaymentId());
    }

    @Test
    void rejectsStatusTransactionWithStaleVersion() throws Exception {
        PaymentItem payment = createPayment("customer-" + UUID.randomUUID(), "key-" + UUID.randomUUID());

        dynamoDbClient.updateItem(request -> request
                .tableName(TABLE_NAME)
                .key(Map.of(
                        PARTITION_KEY_ATTRIBUTE, AttributeValue.fromS(payment.getPk()),
                        SORT_KEY_ATTRIBUTE, AttributeValue.fromS(payment.getSk())
                ))
                .updateExpression("SET #version = :version")
                .expressionAttributeNames(Map.of("#version", "version"))
                .expressionAttributeValues(Map.of(":version", AttributeValue.fromN("1"))));

        payment.setStatus(PaymentStatus.AUTHORIZED.name());
        payment.setVersion(1L);
        payment.setUpdatedAt(Instant.now().toString());

        PaymentEventItem event = new PaymentEventItem();
        event.setPk(paymentPartitionKey(payment.getPaymentId()));
        event.setSk(eventSortKey(Instant.now().toString(), UUID.randomUUID().toString()));
        event.setEventId(UUID.randomUUID().toString());
        event.setPaymentId(payment.getPaymentId());
        event.setType(PaymentStatus.AUTHORIZED.name());
        event.setCreatedAt(Instant.now().toString());

        OutboxItem outbox = new OutboxItem();
        String outboxEventId = UUID.randomUUID().toString();
        outbox.setPk(outboxPartitionKey(outboxEventId));
        outbox.setSk(OUTBOX_EVENT_SORT_KEY);
        outbox.setEntityType(OUTBOX_ENTITY_TYPE);
        outbox.setEventId(outboxEventId);
        outbox.setPaymentId(payment.getPaymentId());
        outbox.setEventType(paymentEventType(PaymentStatus.AUTHORIZED));

        assertThatThrownBy(() -> transactionRepository.changeStatus(
                payment,
                event,
                outbox,
                PaymentStatus.CREATED,
                0L
        )).isInstanceOf(TransactionCanceledException.class);

        assertThat(events(payment.getPaymentId())).extracting(PaymentEventItem::getType)
                .containsExactly("CREATED");
    }

    private PaymentItem createPayment(String customerId, String idempotencyKey) throws Exception {
        return createPayment(new CreatePaymentRequest(customerId, new BigDecimal("10.00"), "rub"), idempotencyKey);
    }

    private PaymentItem createPayment(CreatePaymentRequest request, String idempotencyKey) throws Exception {
        String response = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(response, PaymentItem.class);
    }

    private List<PaymentItem> paymentsFor(String customerId) throws Exception {
        String response = mockMvc.perform(get("/customers/{customerId}/payments", customerId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(response, new TypeReference<>() {
        });
    }

    private List<PaymentEventItem> events(String paymentId) throws Exception {
        String response = mockMvc.perform(get("/payments/{paymentId}/events", paymentId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(response, new TypeReference<>() {
        });
    }
}
