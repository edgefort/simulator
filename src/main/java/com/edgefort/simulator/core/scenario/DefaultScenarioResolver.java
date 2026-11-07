package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.ProviderRegistry;

import java.util.Optional;

/**
 * Precedence: reference override, operation override, test-data rule, operation profile, provider profile,
 * default profile. Provider-wide selections are skipped for operations that cannot express them.
 */
public class DefaultScenarioResolver implements ScenarioResolver {

    private final ProfileCatalog catalog;
    private final ProfileAssignments assignments;
    private final ScenarioOverrideStore overrides;
    private final ScenarioRuleStore rules;
    private final ProviderRegistry registry;

    public DefaultScenarioResolver(
            ProfileCatalog catalog,
            ProfileAssignments assignments,
            ScenarioOverrideStore overrides,
            ScenarioRuleStore rules,
            ProviderRegistry registry
    ) {
        this.catalog = catalog;
        this.assignments = assignments;
        this.overrides = overrides;
        this.rules = rules;
        this.registry = registry;
    }

    @Override
    public ResolvedBehavior resolve(ScenarioQuery query) {
        ProviderOperation operation = registry.operation(query.provider(), query.operation());
        return overrides.consume(query)
                .or(() -> rules.match(query)
                        .map(behavior -> new ResolvedBehavior(behavior, ScenarioSource.RULE)))
                .or(() -> assignments.operationProfile(query.provider(), query.operation())
                        .map(profile -> new ResolvedBehavior(catalog.get(profile), ScenarioSource.OPERATION_PROFILE)))
                .or(() -> supported(operation, assignments.providerProfile(query.provider()))
                        .map(behavior -> new ResolvedBehavior(behavior, ScenarioSource.PROVIDER_PROFILE)))
                .orElseGet(() -> new ResolvedBehavior(catalog.defaultBehavior(), ScenarioSource.DEFAULT_PROFILE));
    }

    private Optional<SimulationBehavior> supported(ProviderOperation operation, Optional<String> profile) {
        return profile.map(catalog::get).filter(behavior -> operation.unsupportedReason(behavior).isEmpty());
    }
}
