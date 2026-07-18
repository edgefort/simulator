package com.edgefort.simulator.core.scenario;

import java.time.Duration;

public record SimulationBehavior(
        SimulationScenario scenario,
        Duration delay,
        Duration transitionDelay
) {
}