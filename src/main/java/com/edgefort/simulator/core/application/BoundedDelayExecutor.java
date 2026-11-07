package com.edgefort.simulator.core.application;

import java.time.Duration;
import java.util.concurrent.Semaphore;

public class BoundedDelayExecutor {

    private final Semaphore heldRequestPermits;
    private final int maximumHeldRequests;
    private final Duration maximumDelay;

    public BoundedDelayExecutor(int maximumHeldRequests, Duration maximumDelay) {
        this.heldRequestPermits = new Semaphore(maximumHeldRequests);
        this.maximumHeldRequests = maximumHeldRequests;
        this.maximumDelay = maximumDelay;
    }

    /**
     * Reserves capacity for a held request before any state changes, so a capacity rejection leaves no side effects.
     */
    public Hold reserve(Duration delay) {
        if (delay.isNegative() || delay.compareTo(maximumDelay) > 0) {
            throw new IllegalStateException("Delay exceeds the configured simulator safety limit");
        }
        if (delay.isZero()) {
            return new Hold(Duration.ZERO, false);
        }
        if (!heldRequestPermits.tryAcquire()) {
            throw new SimulationCapacityException("Maximum concurrent delayed requests reached");
        }
        return new Hold(delay, true);
    }

    public int heldRequests() {
        return maximumHeldRequests - heldRequestPermits.availablePermits();
    }

    public final class Hold implements AutoCloseable {

        private final Duration delay;
        private boolean permitHeld;

        private Hold(Duration delay, boolean permitHeld) {
            this.delay = delay;
            this.permitHeld = permitHeld;
        }

        public void await() {
            if (delay.isZero()) {
                return;
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SimulationCapacityException("Delayed response was interrupted");
            }
        }

        @Override
        public void close() {
            if (permitHeld) {
                permitHeld = false;
                heldRequestPermits.release();
            }
        }
    }
}
