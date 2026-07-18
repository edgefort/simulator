package com.edgefort.simulator.core.scenario;

public class DefaultScenarioResolver implements ScenarioResolver {

    private final ActiveProfileRegistry profileRegistry;
    private final InMemoryScenarioOverrideStore overrideStore;

    public DefaultScenarioResolver(
            ActiveProfileRegistry profileRegistry,
            InMemoryScenarioOverrideStore overrideStore
    ) {
        this.profileRegistry = profileRegistry;
        this.overrideStore = overrideStore;
    }

    @Override
    public SimulationBehavior resolve(ScenarioQuery query) {
        return overrideStore.consume(query.provider(), query.reference())
                .map(profileRegistry::behaviorFor)
                .orElseGet(() -> resolveWithoutOverride(query));
    }

    private SimulationBehavior resolveWithoutOverride(ScenarioQuery query) {
        if (query.requestedScenario() != null && !query.requestedScenario().isBlank()) {
            return profileRegistry.behaviorFor(SimulationScenario.fromExternalValue(query.requestedScenario()));
        }
        return profileRegistry.activeBehavior();
    }
}