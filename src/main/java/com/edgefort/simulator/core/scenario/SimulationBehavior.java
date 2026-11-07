package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.core.state.TransactionStatus;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public record SimulationBehavior(
        String profile,
        SimulationOutcome outcome,
        Duration delay,
        CompletionStatus completesAs,
        Duration transitionDelay,
        boolean received
) {

    /**
     * Status saved when a transactional operation is received, or empty when the provider keeps no record.
     */
    public Optional<TransactionStatus> initialStatus() {
        return switch (outcome) {
            case SUCCESS -> Optional.of(TransactionStatus.SUCCESS);
            case FAILURE -> Optional.of(TransactionStatus.FAILED);
            case PENDING -> Optional.of(TransactionStatus.PENDING);
            case TIMEOUT -> !received
                    ? Optional.empty()
                    : transitionDelay.isZero() && completesAs != CompletionStatus.NEVER
                    ? completesAs.status()
                    : Optional.of(TransactionStatus.PENDING);
        };
    }

    /**
     * Final status scheduled after {@link #transitionDelay()}, when the transaction is saved as pending.
     */
    public Optional<TransactionStatus> scheduledCompletion() {
        return initialStatus().filter(status -> status == TransactionStatus.PENDING)
                .flatMap(status -> completesAs.status());
    }

    /**
     * Every status a transaction query can observe for this behavior.
     */
    public Set<TransactionStatus> observableStatuses() {
        Set<TransactionStatus> statuses = EnumSet.noneOf(TransactionStatus.class);
        initialStatus().ifPresent(statuses::add);
        scheduledCompletion().ifPresent(statuses::add);
        return statuses;
    }
}
