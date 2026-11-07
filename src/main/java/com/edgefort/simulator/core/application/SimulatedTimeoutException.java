package com.edgefort.simulator.core.application;

/**
 * Signals that the client must receive no response. The web layer closes the connection without writing any bytes.
 */
public class SimulatedTimeoutException extends RuntimeException {

    public SimulatedTimeoutException(String provider, String operation) {
        super("Simulated client-visible timeout for %s %s".formatted(provider, operation), null, false, false);
    }
}
