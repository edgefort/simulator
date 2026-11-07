package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.config.SimulationProperties;
import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.ProviderRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Configured and admin-defined test-data rules. Configured rules are restored on reset.
 */
public class ScenarioRuleStore {

    private final Map<String, ScenarioRule> rules = new LinkedHashMap<>();
    private final List<ScenarioRule> configured;
    private final int maximumSize;
    private final ProfileCatalog catalog;
    private final ProviderRegistry registry;

    public ScenarioRuleStore(
            SimulationProperties properties,
            ProfileCatalog catalog,
            ProviderRegistry registry
    ) {
        this.maximumSize = properties.getLimits().getMaxRules();
        this.catalog = catalog;
        this.registry = registry;
        List<ScenarioRule> initial = new ArrayList<>();
        properties.getProviders().forEach((provider, settings) -> {
            for (int index = 0; index < settings.getRules().size(); index++) {
                SimulationProperties.Rule rule = settings.getRules().get(index);
                initial.add(create(
                        "config-%s-%d".formatted(provider, index),
                        provider, rule.getOperation(), rule.getField(), rule.getEquals(), rule.getProfile()
                ));
            }
        });
        if (initial.size() > maximumSize) {
            throw new IllegalStateException("Configured rules exceed simulator.limits.max-rules");
        }
        this.configured = List.copyOf(initial);
        reset();
    }

    public synchronized ScenarioRule add(
            String provider,
            String operation,
            String field,
            String equals,
            String profile
    ) {
        if (rules.size() >= maximumSize) {
            throw new SimulationControlException("Maximum rules reached: " + maximumSize);
        }
        ScenarioRule rule = create(UUID.randomUUID().toString(), provider, operation, field, equals, profile);
        rules.put(rule.id(), rule);
        return rule;
    }

    public synchronized Optional<SimulationBehavior> match(ScenarioQuery query) {
        ProviderOperation operation = registry.operation(query.provider(), query.operation());
        return rules.values().stream()
                .filter(rule -> rule.matches(query))
                .map(rule -> catalog.get(rule.profile()))
                .filter(behavior -> operation.unsupportedReason(behavior).isEmpty())
                .findFirst();
    }

    public synchronized List<ScenarioRule> list() {
        return List.copyOf(rules.values());
    }

    public synchronized boolean remove(String id) {
        return rules.remove(id) != null;
    }

    public synchronized void reset() {
        rules.clear();
        configured.forEach(rule -> rules.put(rule.id(), rule));
    }

    private ScenarioRule create(
            String id,
            String provider,
            String operationName,
            String field,
            String equals,
            String profile
    ) {
        SimulationBehavior behavior = catalog.get(profile);
        List<ProviderOperation> candidates = operationName == null
                ? registry.provider(provider).operations()
                : List.of(registry.operation(provider, operationName));
        List<ProviderOperation> applicable = candidates.stream()
                .filter(operation -> operation.matchFields().contains(field))
                .toList();
        if (applicable.isEmpty()) {
            throw new SimulationControlException(
                    "Field '%s' is not a match field of %s%s".formatted(
                            field, provider, operationName == null ? "" : " " + operationName
                    )
            );
        }
        if (applicable.stream().allMatch(operation -> operation.unsupportedReason(behavior).isPresent())) {
            throw new SimulationControlException(
                    "No %s operation with field '%s' can express profile '%s'".formatted(provider, field, profile)
            );
        }
        return new ScenarioRule(id, provider, operationName, field, equals, profile);
    }
}
