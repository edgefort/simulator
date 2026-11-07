package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.ProviderRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Bounded, TTL-limited, in-memory store of admin overrides. Reference overrides take precedence over operation
 * overrides. An override is only consumed by a request whose operation can express its profile.
 */
public class ScenarioOverrideStore {

    private final Map<String, ScenarioOverride> overrides = new LinkedHashMap<>();
    private final Clock clock;
    private final Duration ttl;
    private final int maximumSize;
    private final ProfileCatalog catalog;
    private final ProviderRegistry registry;

    public ScenarioOverrideStore(
            Clock clock,
            Duration ttl,
            int maximumSize,
            ProfileCatalog catalog,
            ProviderRegistry registry
    ) {
        this.clock = clock;
        this.ttl = ttl;
        this.maximumSize = maximumSize;
        this.catalog = catalog;
        this.registry = registry;
    }

    public synchronized ScenarioOverride add(
            String provider,
            String operation,
            String reference,
            String profile,
            int count
    ) {
        if (count < 1) {
            throw new SimulationControlException("count must be at least 1");
        }
        if (reference != null && count != 1) {
            throw new SimulationControlException("A reference override applies to exactly one request");
        }
        validate(provider, operation, profile);
        purgeExpired();
        if (overrides.size() >= maximumSize) {
            throw new SimulationControlException("Maximum active overrides reached: " + maximumSize);
        }
        ScenarioOverride override = new ScenarioOverride(
                UUID.randomUUID().toString(), provider, operation, reference, profile, count, clock.instant()
        );
        overrides.put(override.id(), override);
        return override;
    }

    public synchronized Optional<ResolvedBehavior> consume(ScenarioQuery query) {
        purgeExpired();
        ProviderOperation operation = registry.operation(query.provider(), query.operation());
        return consumeFirst(query, operation, override -> override.matchesReference(query))
                .map(behavior -> new ResolvedBehavior(behavior, ScenarioSource.REFERENCE_OVERRIDE))
                .or(() -> consumeFirst(query, operation, override -> override.matchesOperation(query))
                        .map(behavior -> new ResolvedBehavior(behavior, ScenarioSource.OPERATION_OVERRIDE)));
    }

    public synchronized List<ScenarioOverride> list() {
        purgeExpired();
        return List.copyOf(overrides.values());
    }

    public synchronized boolean remove(String id) {
        return overrides.remove(id) != null;
    }

    public synchronized void reset() {
        overrides.clear();
    }

    private Optional<SimulationBehavior> consumeFirst(
            ScenarioQuery query,
            ProviderOperation operation,
            Predicate<ScenarioOverride> matcher
    ) {
        for (ScenarioOverride override : new ArrayList<>(overrides.values())) {
            if (!matcher.test(override)) {
                continue;
            }
            SimulationBehavior behavior = catalog.get(override.profile());
            if (operation.unsupportedReason(behavior).isPresent()) {
                continue;
            }
            if (override.remaining() <= 1) {
                overrides.remove(override.id());
            } else {
                overrides.put(override.id(), override.consumeOne());
            }
            return Optional.of(behavior);
        }
        return Optional.empty();
    }

    private void validate(String provider, String operationName, String profile) {
        SimulationBehavior behavior = catalog.get(profile);
        if (operationName != null) {
            registry.operation(provider, operationName).unsupportedReason(behavior).ifPresent(reason -> {
                throw new SimulationControlException(
                        "%s %s cannot use profile '%s': %s".formatted(provider, operationName, profile, reason)
                );
            });
            return;
        }
        boolean supportedSomewhere = registry.provider(provider).operations().stream()
                .anyMatch(operation -> operation.unsupportedReason(behavior).isEmpty());
        if (!supportedSomewhere) {
            throw new SimulationControlException(
                    "No %s operation can express profile '%s'".formatted(provider, profile)
            );
        }
    }

    private void purgeExpired() {
        Instant boundary = clock.instant().minus(ttl);
        Iterator<ScenarioOverride> iterator = overrides.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().createdAt().isBefore(boundary)) {
                iterator.remove();
            }
        }
    }
}
