package com.edgefort.simulator.core.scenario;

import java.time.Instant;

/**
 * Admin-defined behavior for upcoming requests. With a reference it applies only to that provider reference;
 * without one it applies to the next matching requests of the provider, optionally limited to one operation.
 */
public record ScenarioOverride(
        String id,
        String provider,
        String operation,
        String reference,
        String profile,
        int remaining,
        Instant createdAt
) {

    boolean matchesReference(ScenarioQuery query) {
        return reference != null
                && provider.equals(query.provider())
                && reference.equals(query.reference())
                && (operation == null || operation.equals(query.operation()));
    }

    boolean matchesOperation(ScenarioQuery query) {
        return reference == null
                && provider.equals(query.provider())
                && (operation == null || operation.equals(query.operation()));
    }

    ScenarioOverride consumeOne() {
        return new ScenarioOverride(id, provider, operation, reference, profile, remaining - 1, createdAt);
    }
}
