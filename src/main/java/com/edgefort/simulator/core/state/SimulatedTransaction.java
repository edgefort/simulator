package com.edgefort.simulator.core.state;

import java.math.BigDecimal;
import java.time.Instant;

public record SimulatedTransaction(
        TransactionKey key,
        BigDecimal amount,
        TransactionStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public SimulatedTransaction withStatus(TransactionStatus newStatus, Instant now) {
        return new SimulatedTransaction(key, amount, newStatus, createdAt, now);
    }
}