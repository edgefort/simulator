package com.edgefort.simulator.core.scenario;

import java.util.Map;

/**
 * @param reference  provider transaction or session reference, or {@code null} when the operation has none
 * @param attributes request field values named as in the provider specification
 */
public record ScenarioQuery(
        String provider,
        String operation,
        String reference,
        Map<String, String> attributes
) {

    public ScenarioQuery {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
