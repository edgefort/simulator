package com.edgefort.simulator.core.state;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * @param attributes provider-owned request values needed to answer later queries
 */
public record SimulatedTransaction(
        TransactionKey key,
        String operation,
        BigDecimal amount,
        Map<String, String> attributes,
        TransactionStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public SimulatedTransaction {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public SimulatedTransaction withStatus(TransactionStatus newStatus, Instant now) {
        return new SimulatedTransaction(key, operation, amount, attributes, newStatus, createdAt, now);
    }
}
