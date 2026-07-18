package com.edgefort.simulator.core.application;

import java.time.Duration;
import java.util.concurrent.Semaphore;

public class BoundedDelayExecutor {

    private final Semaphore heldRequestPermits;
    private final Duration maximumDelay;

    public BoundedDelayExecutor(int maximumHeldRequests, Duration maximumDelay) {
        this.heldRequestPermits = new Semaphore(maximumHeldRequests);
        this.maximumDelay = maximumDelay;
    }

    public void hold(Duration delay) {
        if (delay.isNegative() || delay.compareTo(maximumDelay) > 0) {
            throw new IllegalArgumentException("Delay exceeds the configured simulator safety limit");
        }
        if (delay.isZero()) {
            return;
        }
        if (!heldRequestPermits.tryAcquire()) {
            throw new SimulationCapacityException("Maximum concurrent delayed requests reached");
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SimulationCapacityException("Delayed response was interrupted");
        } finally {
            heldRequestPermits.release();
        }
    }
}