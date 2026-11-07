package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.core.state.TransactionStatus;

import java.util.Optional;

/**
 * Final state of a pending or timed-out transaction.
 */
public enum CompletionStatus {
    SUCCESS(TransactionStatus.SUCCESS),
    FAILED(TransactionStatus.FAILED),
    REVERSED(TransactionStatus.REVERSED),
    NEVER(null);

    private final TransactionStatus status;

    CompletionStatus(TransactionStatus status) {
        this.status = status;
    }

    public Optional<TransactionStatus> status() {
        return Optional.ofNullable(status);
    }
}
