package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.config.SimulationProperties;
import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.ProviderRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profiles assigned per provider and per provider operation. Assignments start from configuration, can be changed
 * through the admin API, and return to configuration on reset.
 */
public class ProfileAssignments {

    private static final String ALL_OPERATIONS = "";

    private final ProfileCatalog catalog;
    private final ProviderRegistry registry;
    private final Map<Key, String> configured;
    private final Map<Key, String> assignments = new ConcurrentHashMap<>();

    public ProfileAssignments(SimulationProperties properties, ProfileCatalog catalog, ProviderRegistry registry) {
        this.catalog = catalog;
        this.registry = registry;
        Map<Key, String> initial = new ConcurrentHashMap<>();
        properties.getProviders().forEach((provider, settings) -> {
            if (settings.getProfile() != null) {
                validateProviderProfile(provider, settings.getProfile());
                initial.put(new Key(provider, ALL_OPERATIONS), settings.getProfile());
            }
            settings.getOperationProfiles().forEach((operation, profile) -> {
                validateOperationProfile(provider, operation, profile);
                initial.put(new Key(provider, operation), profile);
            });
        });
        this.configured = Map.copyOf(initial);
        reset();
    }

    public void assignProvider(String provider, String profile) {
        validateProviderProfile(provider, profile);
        assignments.put(new Key(provider, ALL_OPERATIONS), profile);
    }

    public void clearProvider(String provider) {
        registry.provider(provider);
        assignments.remove(new Key(provider, ALL_OPERATIONS));
    }

    public void assignOperation(String provider, String operation, String profile) {
        validateOperationProfile(provider, operation, profile);
        assignments.put(new Key(provider, operation), profile);
    }

    public void clearOperation(String provider, String operation) {
        registry.operation(provider, operation);
        assignments.remove(new Key(provider, operation));
    }

    public Optional<String> providerProfile(String provider) {
        return Optional.ofNullable(assignments.get(new Key(provider, ALL_OPERATIONS)));
    }

    public Optional<String> operationProfile(String provider, String operation) {
        return Optional.ofNullable(assignments.get(new Key(provider, operation)));
    }

    public Map<String, String> operationProfiles(String provider) {
        Map<String, String> result = new java.util.TreeMap<>();
        assignments.forEach((key, profile) -> {
            if (key.provider().equals(provider) && !key.operation().equals(ALL_OPERATIONS)) {
                result.put(key.operation(), profile);
            }
        });
        return result;
    }

    public void reset() {
        assignments.clear();
        assignments.putAll(configured);
    }

    private void validateProviderProfile(String provider, String profile) {
        SimulationBehavior behavior = catalog.get(profile);
        boolean supportedSomewhere = registry.provider(provider).operations().stream()
                .anyMatch(operation -> operation.unsupportedReason(behavior).isEmpty());
        if (!supportedSomewhere) {
            throw new SimulationControlException(
                    "No %s operation can express profile '%s'".formatted(provider, profile)
            );
        }
    }

    private void validateOperationProfile(String provider, String operationName, String profile) {
        ProviderOperation operation = registry.operation(provider, operationName);
        operation.unsupportedReason(catalog.get(profile)).ifPresent(reason -> {
            throw new SimulationControlException(
                    "%s %s cannot use profile '%s': %s".formatted(provider, operationName, profile, reason)
            );
        });
    }

    private record Key(String provider, String operation) {
    }
}
