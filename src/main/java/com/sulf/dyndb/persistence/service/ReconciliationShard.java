package com.sulf.dyndb.persistence.service;

public final class ReconciliationShard {

    private static final int SHARD_COUNT = 10;
    private ReconciliationShard(){}

    public static int resolve(String paymentId){
        return Math.floorMod(
                paymentId.hashCode(),
                SHARD_COUNT
        );
    }

    public static int shardCount() {
        return SHARD_COUNT;
    }
}
