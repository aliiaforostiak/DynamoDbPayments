package com.sulf.dyndb.persistence.service;

import com.sulf.dyndb.exception.ConcurrentPaymentModificationException;
import com.sulf.dyndb.exception.IdempotencyConflictException;
import com.sulf.dyndb.exception.InvalidCursorException;
import com.sulf.dyndb.exception.PaymentNotFoundException;
import com.sulf.dyndb.persistence.domain.*;
import com.sulf.dyndb.persistence.repository.IdempotencyRepository;
import com.sulf.dyndb.persistence.repository.PaymentRepository;
import com.sulf.dyndb.persistence.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static com.sulf.dyndb.persistence.domain.DynamoDbSchema.*;

@Service
public class PaymentService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private final PaymentRepository paymentRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentStateMachine stateMachine;
    private final CursorCodec cursorCodec;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            IdempotencyRepository idempotencyRepository,
            PaymentTransactionRepository transactionRepository,
            PaymentStateMachine stateMachine,
            CursorCodec cursorCodec,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionRepository = transactionRepository;
        this.stateMachine = stateMachine;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    public PaymentItem create(
            String idempotencyKey,
            String customerId,
            BigDecimal amount,
            String currency
    ) {
        String requestHash = calculateRequestHash(customerId, amount, currency);

        Optional<IdempotencyItem> existing = idempotencyRepository.findByKey(idempotencyKey);

        if (existing.isPresent()) {
            return resolveExistingRequest(
                    existing.get(),
                    requestHash
            );
        }

        String paymentId = UUID.randomUUID().toString();

        Instant now = clock.instant();

        String createdAt = now.toString();
        String paymentPk = customerPartitionKey(customerId);
        String paymentSk = paymentSortKey(createdAt, paymentId);

        PaymentItem payment = new PaymentItem();

        payment.setPk(paymentPk);
        payment.setSk(paymentSk);

        payment.setGsi1Pk(paymentPartitionKey(paymentId));
        payment.setGsi2Pk(statusPartitionKey(PaymentStatus.CREATED));
        payment.setGsi2Sk(createdAt + KEY_PART_SEPARATOR + paymentId);

        payment.setPaymentId(paymentId);
        payment.setCustomerId(customerId);
        payment.setAmount(amount);
        payment.setCurrency(currency.toUpperCase(Locale.ROOT));
        payment.setStatus(PaymentStatus.CREATED.name());
        payment.setCreatedAt(createdAt);
        payment.setUpdatedAt(createdAt);
        payment.setVersion(0L);

        String eventId = UUID.randomUUID().toString();
        PaymentEventItem createdEvent = new PaymentEventItem();

        createdEvent.setPk(paymentPartitionKey(paymentId));
        createdEvent.setSk(eventSortKey(createdAt, eventId));

        createdEvent.setEventId(eventId);
        createdEvent.setPaymentId(paymentId);
        createdEvent.setType(PaymentStatus.CREATED.name());
        createdEvent.setCreatedAt(createdAt);

        IdempotencyItem idempotency = new IdempotencyItem();
        long expiresAt = now.plus(IDEMPOTENCY_TTL).getEpochSecond();

        idempotency.setPk(idempotencyPartitionKey(idempotencyKey));
        idempotency.setSk(IDEMPOTENCY_REQUEST_SORT_KEY);

        idempotency.setRequestHash(requestHash);

        idempotency.setPaymentId(paymentId);
        idempotency.setPaymentPk(paymentPk);
        idempotency.setPaymentSk(paymentSk);

        idempotency.setCreatedAt(createdAt);
        idempotency.setExpiresAt(expiresAt);

        try {
            transactionRepository.createPayment(
                    payment,
                    createdEvent,
                    idempotency,
                    now.getEpochSecond()
            );

            return payment;

        } catch (TransactionCanceledException exception) {
            Optional<IdempotencyItem> concurrentRequest = idempotencyRepository.findByKey(idempotencyKey);

            if (concurrentRequest.isPresent()) {
                return resolveExistingRequest(concurrentRequest.get(), requestHash);
            }
            throw exception;
        }
    }

    public PaymentItem findByPaymentId(
            String paymentId
    ) {
        return paymentRepository
                .findByPaymentId(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(paymentId));
    }

    public List<PaymentItem> findByCustomer(
            String customerId
    ) {
        return paymentRepository.findAllByCustomerId(customerId);
    }

    public List<PaymentItem> findByCustomerAndPeriod(
            String customerId,
            Instant from,
            Instant to
    ) {
        return paymentRepository
                .findAllByCustomerIdAndPeriod(
                        customerId,
                        from,
                        to
                );
    }

    private PaymentItem resolveExistingRequest(
            IdempotencyItem idempotency,
            String requestHash
    ) {
        if (!idempotency
                .getRequestHash()
                .equals(requestHash)) {

            throw new IdempotencyConflictException("Idempotency key was already used for a different request");
        }

        return paymentRepository
                .findByPrimaryKey(
                        idempotency.getPaymentPk(),
                        idempotency.getPaymentSk()
                )
                .orElseThrow(() ->
                        new IllegalStateException("Idempotency record exists, " + "but payment was not found"));
    }

    private String calculateRequestHash(
            String customerId,
            BigDecimal amount,
            String currency
    ) {
        String canonicalRequest = customerId
                + "|"
                + amount
                .stripTrailingZeros()
                .toPlainString()
                + "|"
                + currency
                .toUpperCase(Locale.ROOT);

        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));

            return HexFormat
                    .of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    public PaymentItem authorize(String paymentId) {
        return changeStatus(paymentId, PaymentStatus.CAPTURED);
    }

    public PaymentItem capture(String paymentId) {
        return changeStatus(paymentId, PaymentStatus.AUTHORIZED);
    }

    public PaymentItem fail(String paymentId) {
        return changeStatus(paymentId, PaymentStatus.FAILED);
    }

    public PaymentItem refund(String paymentId) {
        return changeStatus(paymentId, PaymentStatus.REFUNDED);
    }

    public PaymentItem changeStatus(String paymentId, PaymentStatus newStatus) {

        PaymentItem payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        PaymentStatus currentStatus = PaymentStatus.valueOf(payment.getStatus());
        stateMachine.validateTransition(currentStatus, newStatus);
        long expectedVersion = payment.getVersion();

        String now = clock.instant().toString();

        payment.setStatus(newStatus.name());
        payment.setUpdatedAt(now);
        payment.setGsi2Pk(statusPartitionKey(newStatus));
        payment.setGsi2Sk(now + KEY_PART_SEPARATOR + paymentId);
        payment.setVersion(expectedVersion + 1);
        String eventId = UUID.randomUUID().toString();

        PaymentEventItem event = new PaymentEventItem();

        event.setPk(paymentPartitionKey(paymentId));
        event.setSk(eventSortKey(now, eventId));

        event.setEventId(eventId);
        event.setPaymentId(paymentId);
        event.setType(newStatus.name());
        event.setCreatedAt(now);

        try {
            transactionRepository.changeStatus(
                    payment,
                    event,
                    currentStatus,
                    expectedVersion
            );

            return payment;

        } catch (TransactionCanceledException exception) {

            boolean optimisticLockFailed =
                    !exception.cancellationReasons().isEmpty()
                            && "ConditionalCheckFailed".equals(
                            exception
                                    .cancellationReasons()
                                    .getFirst()
                                    .code()
                    );

            if (optimisticLockFailed) {
                throw new ConcurrentPaymentModificationException(
                        paymentId,
                        expectedVersion
                );
            }

            throw exception;
        }
    }

    public PaymentPageResponse findPageByCustomerId(
            String customerId,
            int limit,
            String cursor,
            SortDirection sortDirection
    ) {
        Map<String, AttributeValue> exclusiveStartKey = decodeCursor(customerId, cursor);
        PaymentPage page = paymentRepository.findPageByCustomerId(
                customerId,
                limit,
                exclusiveStartKey,
                sortDirection
        );
        String nextCursor = encodeCursor(page.lastEvaluatedKey());
        return new PaymentPageResponse(
                page.items(),
                nextCursor
        );
    }

    private Map<String, AttributeValue> decodeCursor(String customerId, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        PaymentCursor decode = cursorCodec.decode(cursor);
        String expectedPk = customerPartitionKey(customerId);

        if (!expectedPk.equals(decode.pk())) {
            throw new InvalidCursorException("Invalid pagination cursor");
        }

        return Map.of(PARTITION_KEY_ATTRIBUTE,
                AttributeValue
                        .builder()
                        .s(decode.pk())
                        .build(),
                SORT_KEY_ATTRIBUTE,
                AttributeValue
                        .builder()
                        .s(decode.sk())
                        .build());
    }

    private String encodeCursor(
            Map<String, AttributeValue> lastEvaluatedKey
    ) {
        if (lastEvaluatedKey == null
                || lastEvaluatedKey.isEmpty()) {
            return null;
        }

        PaymentCursor cursor =
                new PaymentCursor(
                        lastEvaluatedKey.get(PARTITION_KEY_ATTRIBUTE).s(),
                        lastEvaluatedKey.get(SORT_KEY_ATTRIBUTE).s()
                );

        return cursorCodec.encode(cursor);
    }
}
