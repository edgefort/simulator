package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.scenario.SimulationScenario;
import com.edgefort.simulator.core.state.TransactionStatus;

public record TransferSimulationResult(
        String reference,
        TransactionStatus status,
        SimulationScenario scenario,
        boolean duplicate,
        boolean timeout
) {
}