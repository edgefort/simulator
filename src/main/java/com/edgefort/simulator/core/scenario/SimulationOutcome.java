package com.edgefort.simulator.core.scenario;

/**
 * Provider-neutral result of one simulated operation. Each provider maps it to a documented response.
 */
public enum SimulationOutcome {
    /** The provider accepts and completes the operation. */
    SUCCESS,
    /** The provider declares the operation failed. */
    FAILURE,
    /** The provider accepts the operation and reports it as still processing. */
    PENDING,
    /** The client receives no response: the connection is held and then closed without any bytes. */
    TIMEOUT
}
