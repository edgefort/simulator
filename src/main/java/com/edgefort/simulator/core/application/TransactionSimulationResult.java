package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.state.SimulatedTransaction;

/**
 * @param transaction the transaction created by this request, or the existing one when {@code duplicate}
 */
public record TransactionSimulationResult(SimulatedTransaction transaction, boolean duplicate) {
}
