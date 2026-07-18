package com.edgefort.simulator.core.scenario;

public record ScenarioOverride(
        String provider,
        String reference,
        SimulationScenario scenario
) {
}