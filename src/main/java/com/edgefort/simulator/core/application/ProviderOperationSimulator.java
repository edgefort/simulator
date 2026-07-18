package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.scenario.ScenarioQuery;
import com.edgefort.simulator.core.scenario.ScenarioResolver;
import com.edgefort.simulator.core.scenario.SimulationBehavior;
import com.edgefort.simulator.core.scenario.SimulationScenario;

public class ProviderOperationSimulator {

    private final ScenarioResolver scenarioResolver;
    private final BoundedDelayExecutor delayExecutor;

    public ProviderOperationSimulator(ScenarioResolver scenarioResolver, BoundedDelayExecutor delayExecutor) {
        this.scenarioResolver = scenarioResolver;
        this.delayExecutor = delayExecutor;
    }

    public SimulationBehavior simulate(
            String provider,
            String operation,
            String reference,
            String requestedScenario
    ) {
        SimulationBehavior behavior = scenarioResolver.resolve(
                new ScenarioQuery(provider, operation, reference, requestedScenario)
        );
        if (behavior.scenario() == SimulationScenario.DELAYED_SUCCESS
                || behavior.scenario() == SimulationScenario.TIMEOUT) {
            delayExecutor.hold(behavior.delay());
        }
        return behavior;
    }
}