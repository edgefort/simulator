package com.edgefort.simulator.core.scenario;

/**
 * Rejected simulator control-plane input, such as an unknown profile or a profile the provider cannot express.
 */
public class SimulationControlException extends RuntimeException {

    public SimulationControlException(String message) {
        super(message);
    }
}
