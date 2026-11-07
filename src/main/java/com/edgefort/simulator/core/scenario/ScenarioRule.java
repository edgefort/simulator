package com.edgefort.simulator.core.scenario;

/**
 * Selects a profile when a request field, named as in the provider specification, equals a value.
 */
public record ScenarioRule(
        String id,
        String provider,
        String operation,
        String field,
        String equals,
        String profile
) {

    boolean matches(ScenarioQuery query) {
        return provider.equals(query.provider())
                && (operation == null || operation.equals(query.operation()))
                && equals.equals(query.attributes().get(field));
    }
}
