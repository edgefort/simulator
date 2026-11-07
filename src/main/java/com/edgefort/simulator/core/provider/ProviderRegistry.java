package com.edgefort.simulator.core.provider;

import com.edgefort.simulator.config.SimulationProperties;
import com.edgefort.simulator.core.scenario.SimulationControlException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ProviderRegistry {

    private final Map<String, SimulatedProvider> providers;
    private final Map<String, SimulationProperties.Provider> settings;

    public ProviderRegistry(List<SimulatedProvider> modules, SimulationProperties properties) {
        Map<String, SimulatedProvider> byId = new LinkedHashMap<>();
        for (SimulatedProvider module : modules) {
            if (byId.put(module.id(), module) != null) {
                throw new IllegalStateException("Duplicate simulated provider id: " + module.id());
            }
            Set<String> names = new HashSet<>();
            module.operations().forEach(operation -> {
                if (!names.add(operation.name())) {
                    throw new IllegalStateException(
                            "Duplicate operation %s in provider %s".formatted(operation.name(), module.id())
                    );
                }
            });
        }
        for (SimulatedProvider first : byId.values()) {
            for (SimulatedProvider second : byId.values()) {
                if (first != second && second.basePackage().startsWith(first.basePackage() + ".")) {
                    throw new IllegalStateException(
                            "Provider packages must not nest: %s contains %s".formatted(first.id(), second.id())
                    );
                }
            }
        }
        Set<String> configured = properties.getProviders().keySet();
        if (!configured.equals(byId.keySet())) {
            throw new IllegalStateException(
                    "simulator.providers %s must match the provider modules %s".formatted(configured, byId.keySet())
            );
        }
        this.providers = Map.copyOf(byId);
        this.settings = Map.copyOf(properties.getProviders());
    }

    public SimulatedProvider provider(String id) {
        SimulatedProvider provider = providers.get(id);
        if (provider == null) {
            throw new SimulationControlException("Unknown provider: " + id);
        }
        return provider;
    }

    public ProviderOperation operation(String providerId, String operationName) {
        return provider(providerId).operations().stream()
                .filter(operation -> operation.name().equals(operationName))
                .findFirst()
                .orElseThrow(() -> new SimulationControlException(
                        "Unknown operation %s for provider %s".formatted(operationName, providerId)
                ));
    }

    public SimulationProperties.Provider settings(String providerId) {
        provider(providerId);
        return settings.get(providerId);
    }

    public List<SimulatedProvider> providers() {
        return providers.values().stream()
                .sorted(java.util.Comparator.comparing(SimulatedProvider::id))
                .toList();
    }

    /**
     * Provider owning a request handler of the given type, if any.
     */
    public Optional<SimulatedProvider> owner(Class<?> handlerType) {
        String packageName = handlerType.getPackageName();
        return providers.values().stream()
                .filter(provider -> packageName.equals(provider.basePackage())
                        || packageName.startsWith(provider.basePackage() + "."))
                .findFirst();
    }
}
