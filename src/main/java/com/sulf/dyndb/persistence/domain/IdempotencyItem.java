package com.sulf.dyndb.persistence.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
public class IdempotencyItem extends BaseDynamoDbBean {

    private String requestHash;

    private String paymentId;
    private String paymentPk;
    private String paymentSk;
    private Long expiresAt;

    public IdempotencyItem(){}

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentPk() {
        return paymentPk;
    }

    public void setPaymentPk(String paymentPk) {
        this.paymentPk = paymentPk;
    }

    public String getPaymentSk() {
        return paymentSk;
    }

    public void setPaymentSk(String paymentSk) {
        this.paymentSk = paymentSk;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
