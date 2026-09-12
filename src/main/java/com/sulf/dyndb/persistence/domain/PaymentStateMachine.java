package com.sulf.dyndb.persistence.domain;

import com.sulf.dyndb.exception.InvalidPaymentStateException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    PaymentStatus.CREATED,
                    Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED),
                    PaymentStatus.AUTHORIZED,
                    Set.of(PaymentStatus.CAPTURED, PaymentStatus.FAILED),
                    PaymentStatus.CAPTURED,
                    Set.of(PaymentStatus.REFUNDED)
            );

    public void validateTransition(PaymentStatus currentStatus, PaymentStatus newStatus) {
        Set<PaymentStatus> allowed =
                ALLOWED_TRANSITIONS.getOrDefault(
                        currentStatus,
                        Set.of()
                );

        if (!allowed.contains(newStatus)) {
            throw new InvalidPaymentStateException(
                    "Transition " + currentStatus + " -> " + newStatus + " is not allowed"
            );
        }
    }

}
