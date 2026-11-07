package com.edgefort.simulator.core.provider;

import com.edgefort.simulator.core.scenario.SimulationBehavior;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.TransactionStatus;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * What one provider operation can express according to its specification.
 *
 * @param transactional whether the operation creates a transaction that a provider query can observe later
 * @param outcomes      outcomes the operation response can represent
 * @param queryStatuses transaction statuses the provider's query contract can report; empty when not transactional
 * @param matchFields   request fields, named as in the specification, that test-data rules may match
 */
public record ProviderOperation(
        String name,
        boolean transactional,
        Set<SimulationOutcome> outcomes,
        Set<TransactionStatus> queryStatuses,
        Set<String> matchFields
) {

    public ProviderOperation {
        outcomes = Set.copyOf(outcomes);
        queryStatuses = Set.copyOf(queryStatuses);
        matchFields = Set.copyOf(matchFields);
        if (!outcomes.contains(SimulationOutcome.SUCCESS)) {
            throw new IllegalArgumentException(name + " must support SUCCESS");
        }
        if (!transactional && (!queryStatuses.isEmpty() || outcomes.contains(SimulationOutcome.PENDING))) {
            throw new IllegalArgumentException(name + " is not transactional and cannot report transaction states");
        }
    }

    public static ProviderOperation stateless(String name, Set<String> matchFields, SimulationOutcome... outcomes) {
        return new ProviderOperation(name, false, Set.of(outcomes), Set.of(), matchFields);
    }

    public static ProviderOperation transactional(
            String name,
            Set<String> matchFields,
            Set<SimulationOutcome> outcomes,
            Set<TransactionStatus> queryStatuses
    ) {
        return new ProviderOperation(name, true, outcomes, queryStatuses, matchFields);
    }

    /**
     * Explains why this operation cannot express the behavior, or returns empty when it can.
     */
    public Optional<String> unsupportedReason(SimulationBehavior behavior) {
        if (!outcomes.contains(behavior.outcome())) {
            return Optional.of("the specification has no %s response for this operation"
                    .formatted(behavior.outcome().name().toLowerCase()));
        }
        if (!transactional) {
            return Optional.empty();
        }
        Set<TransactionStatus> missing = EnumSet.noneOf(TransactionStatus.class);
        missing.addAll(behavior.observableStatuses());
        missing.removeAll(queryStatuses);
        if (!missing.isEmpty()) {
            return Optional.of("the specification cannot report transaction status " + missing);
        }
        return Optional.empty();
    }
}
