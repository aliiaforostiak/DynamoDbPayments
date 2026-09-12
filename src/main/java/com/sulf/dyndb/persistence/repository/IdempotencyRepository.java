package com.sulf.dyndb.persistence.repository;

import com.sulf.dyndb.persistence.domain.IdempotencyItem;

import java.util.Optional;

public interface IdempotencyRepository {
    Optional<IdempotencyItem> findByKey(
            String idempotencyKey
    );
}
