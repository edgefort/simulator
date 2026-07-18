package com.edgefort.simulator.core.scenario;

public record ScenarioQuery(
        String provider,
        String operation,
        String reference,
        String requestedScenario
) {
}