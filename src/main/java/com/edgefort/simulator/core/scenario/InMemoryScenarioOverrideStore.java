package com.edgefort.simulator.core.scenario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryScenarioOverrideStore {

    private final ConcurrentHashMap<OverrideKey, StoredOverride> overrides = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;
    private final int maximumSize;

    public InMemoryScenarioOverrideStore(Clock clock, Duration ttl, int maximumSize) {
        this.clock = clock;
        this.ttl = ttl;
        this.maximumSize = maximumSize;
    }

    public synchronized void put(ScenarioOverride override) {
        purgeExpired();
        OverrideKey key = new OverrideKey(override.provider(), override.reference());
        if (!overrides.containsKey(key) && overrides.size() >= maximumSize) {
            overrides.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().createdAt()))
                    .map(java.util.Map.Entry::getKey)
                    .ifPresent(overrides::remove);
        }
        overrides.put(key, new StoredOverride(override.scenario(), clock.instant()));
    }

    public synchronized Optional<SimulationScenario> consume(String provider, String reference) {
        purgeExpired();
        StoredOverride stored = overrides.remove(new OverrideKey(provider, reference));
        return Optional.ofNullable(stored).map(StoredOverride::scenario);
    }

    public void reset() {
        overrides.clear();
    }

    private void purgeExpired() {
        Instant expiryBoundary = clock.instant().minus(ttl);
        overrides.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(expiryBoundary));
    }

    private record OverrideKey(String provider, String reference) {
    }

    private record StoredOverride(SimulationScenario scenario, Instant createdAt) {
    }
}