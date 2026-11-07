package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.scenario.ScenarioQuery;
import com.edgefort.simulator.core.scenario.ScenarioResolver;
import com.edgefort.simulator.core.scenario.SimulationBehavior;
import com.edgefort.simulator.core.scenario.SimulationOutcome;

import java.util.Map;

/**
 * Simulates operations that keep no transaction state, such as name or balance enquiry.
 */
public class ProviderOperationSimulator {

    private final ScenarioResolver scenarioResolver;
    private final BoundedDelayExecutor delayExecutor;
    private final SimulationTelemetry telemetry;

    public ProviderOperationSimulator(
            ScenarioResolver scenarioResolver,
            BoundedDelayExecutor delayExecutor,
            SimulationTelemetry telemetry
    ) {
        this.scenarioResolver = scenarioResolver;
        this.delayExecutor = delayExecutor;
        this.telemetry = telemetry;
    }

    /**
     * Returns {@link SimulationOutcome#SUCCESS} or {@link SimulationOutcome#FAILURE} after the profile delay, or
     * throws {@link SimulatedTimeoutException} after holding the request.
     */
    public SimulationOutcome simulate(
            String provider,
            String operation,
            String reference,
            Map<String, String> attributes
    ) {
        var resolved = scenarioResolver.resolve(new ScenarioQuery(provider, operation, reference, attributes));
        SimulationBehavior behavior = resolved.behavior();
        try (BoundedDelayExecutor.Hold hold = delayExecutor.reserve(behavior.delay())) {
            telemetry.selected(provider, operation, resolved);
            hold.await();
        }
        if (behavior.outcome() == SimulationOutcome.TIMEOUT) {
            throw new SimulatedTimeoutException(provider, operation);
        }
        return behavior.outcome();
    }
}
