package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.PaymentEventItem;

import java.util.List;

public interface PaymentEventRepository {

    void save(PaymentEventItem event);

    List<PaymentEventItem> findAllByPaymentId(String paymentId);
}