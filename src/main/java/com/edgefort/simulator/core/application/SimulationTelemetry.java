package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.scenario.ResolvedBehavior;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records the selected behavior for each simulated request. Request field values are never logged.
 */
public class SimulationTelemetry {

    private static final Logger logger = LoggerFactory.getLogger(SimulationTelemetry.class);

    private final MeterRegistry meterRegistry;

    public SimulationTelemetry(MeterRegistry meterRegistry, BoundedDelayExecutor delayExecutor) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("simulator.held.requests", delayExecutor, BoundedDelayExecutor::heldRequests)
                .description("Provider requests currently held by a delay or simulated timeout")
                .register(meterRegistry);
    }

    public void selected(String provider, String operation, ResolvedBehavior resolved) {
        Counter.builder("simulator.simulations")
                .description("Simulated provider operations by selected profile and outcome")
                .tag("provider", provider)
                .tag("operation", operation)
                .tag("profile", resolved.behavior().profile())
                .tag("source", resolved.source().name().toLowerCase())
                .tag("outcome", resolved.behavior().outcome().name().toLowerCase())
                .register(meterRegistry)
                .increment();
        logger.info(
                "Simulating provider={} operation={} profile={} source={} outcome={}",
                provider,
                operation,
                resolved.behavior().profile(),
                resolved.source().name().toLowerCase(),
                resolved.behavior().outcome().name().toLowerCase()
        );
    }

    public void duplicate(String provider, String operation) {
        Counter.builder("simulator.duplicates")
                .description("Requests rejected as duplicates of an in-memory transaction")
                .tag("provider", provider)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
        logger.info("Duplicate provider={} operation={}", provider, operation);
    }
}
