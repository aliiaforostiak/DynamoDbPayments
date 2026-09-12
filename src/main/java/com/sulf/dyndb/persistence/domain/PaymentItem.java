package com.sulf.dyndb.persistence.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.math.BigDecimal;

@DynamoDbBean
public class PaymentItem extends BaseDynamoDbBean {

    private String paymentId;
    private String customerId;

    private BigDecimal amount;
    private String currency;
    private String status;
    private String updatedAt;

    private String gsi1Pk;
    private String gsi2Pk;
    private String gsi2Sk;

    private Long version;

    public PaymentItem() {
    }

    @DynamoDbSecondaryPartitionKey(indexNames = DynamoDbSchema.PAYMENT_ID_INDEX)
    public String getGsi1Pk() {
        return gsi1Pk;
    }

    public void setGsi1Pk(String gsi1Pk) {
        this.gsi1Pk = gsi1Pk;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = DynamoDbSchema.STATUS_UPDATED_AT_INDEX)
    public String getGsi2Pk() {
        return gsi2Pk;
    }

    public void setGsi2Pk(String gsi2Pk) {
        this.gsi2Pk = gsi2Pk;
    }

    @DynamoDbSecondarySortKey(indexNames = DynamoDbSchema.STATUS_UPDATED_AT_INDEX)
    public String getGsi2Sk() {
        return gsi2Sk;
    }

    public void setGsi2Sk(String gsi2Sk) {
        this.gsi2Sk = gsi2Sk;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

}
