package com.sulf.dyndb.persistence.controller;

import com.sulf.dyndb.persistence.domain.PaymentEventItem;
import com.sulf.dyndb.persistence.domain.PaymentItem;
import com.sulf.dyndb.persistence.domain.PaymentPageResponse;
import com.sulf.dyndb.persistence.service.PaymentEventService;
import com.sulf.dyndb.persistence.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentEventService paymentEventService;

    public PaymentController(
            PaymentService paymentService,
            PaymentEventService paymentEventService
    ) {
        this.paymentService = paymentService;
        this.paymentEventService = paymentEventService;
    }

    @PostMapping("/payments")
    public PaymentItem create(
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid @RequestBody
            CreatePaymentRequest request
    ) {
        return paymentService.create(
                idempotencyKey,
                request.customerId(),
                request.amount(),
                request.currency()
        );
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentItem findByPaymentId(
            @PathVariable String paymentId
    ) {
        return paymentService
                .findByPaymentId(paymentId);
    }

    @GetMapping("/customers/{customerId}/payments")
    public List<PaymentItem> findByCustomer(
            @PathVariable String customerId,

            @RequestParam(required = false)
            Instant from,

            @RequestParam(required = false)
            Instant to
    ) {
        if (from != null && to != null) {
            return paymentService
                    .findByCustomerAndPeriod(
                            customerId,
                            from,
                            to
                    );
        }

        return paymentService
                .findByCustomer(customerId);
    }

    @PostMapping("/payments/{paymentId}/events")
    public PaymentEventItem addEvent(
            @PathVariable String paymentId,
            @Valid @RequestBody AddPaymentEventRequest request
    ) {
        return paymentEventService.addEvent(
                paymentId,
                request.type()
        );
    }

    @GetMapping("/payments/{paymentId}/events")
    public List<PaymentEventItem> findEvents(
            @PathVariable String paymentId
    ) {
        return paymentEventService
                .findAll(paymentId);
    }

    @PostMapping("/payments/{paymentId}/authorize")
    public PaymentItem authorize(
            @PathVariable String paymentId
    ) {
        return paymentService.authorize(paymentId);
    }

    @PostMapping("/payments/{paymentId}/capture")
    public PaymentItem capture(
            @PathVariable String paymentId
    ) {
        return paymentService.capture(paymentId);
    }

    @PostMapping("/payments/{paymentId}/fail")
    public PaymentItem fail(
            @PathVariable String paymentId
    ) {
        return paymentService.fail(paymentId);
    }

    @PostMapping("/payments/{paymentId}/refund")
    public PaymentItem refund(
            @PathVariable String paymentId
    ) {
        return paymentService.refund(paymentId);
    }

    @GetMapping("/customers/{customerId}/page")
    public PaymentPageResponse findPage(@PathVariable String customerId, @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor) {
        return paymentService.findPageByCustomerId(customerId, limit, cursor);
    }
}
