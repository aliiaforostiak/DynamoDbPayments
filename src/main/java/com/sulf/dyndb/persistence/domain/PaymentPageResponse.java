package com.sulf.dyndb.persistence.domain;

import java.util.List;

public record PaymentPageResponse(List<PaymentItem> items, String nextCursor) {
}
