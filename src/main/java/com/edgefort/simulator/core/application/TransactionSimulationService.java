package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.scenario.ScenarioQuery;
import com.edgefort.simulator.core.scenario.ScenarioResolver;
import com.edgefort.simulator.core.scenario.SimulationBehavior;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.core.state.TransactionKey;
import com.edgefort.simulator.core.state.TransactionStatus;
import com.edgefort.simulator.core.state.TransactionStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Simulates operations that create a provider transaction observable by later queries.
 *
 * <p>The transaction is recorded when the request is received, before any response delay, so a client that times
 * out and queries sees what the provider recorded. A simulated timeout then closes the connection without a
 * response; whether a record exists depends on the profile's {@code received} setting.</p>
 */
public class TransactionSimulationService {

    private final ScenarioResolver scenarioResolver;
    private final TransactionStore transactionStore;
    private final BoundedDelayExecutor delayExecutor;
    private final CompletionScheduler completionScheduler;
    private final SimulationTelemetry telemetry;
    private final Clock clock;

    public TransactionSimulationService(
            ScenarioResolver scenarioResolver,
            TransactionStore transactionStore,
            BoundedDelayExecutor delayExecutor,
            CompletionScheduler completionScheduler,
            SimulationTelemetry telemetry,
            Clock clock
    ) {
        this.scenarioResolver = scenarioResolver;
        this.transactionStore = transactionStore;
        this.delayExecutor = delayExecutor;
        this.completionScheduler = completionScheduler;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    /**
     * Returns the created or duplicate transaction. Throws {@link SimulatedTimeoutException} when the client must
     * receive no response.
     */
    public TransactionSimulationResult simulate(TransactionCommand command) {
        TransactionKey key = new TransactionKey(command.provider(), command.reference());
        Optional<SimulatedTransaction> existing = transactionStore.find(key);
        if (existing.isPresent()) {
            telemetry.duplicate(command.provider(), command.operation());
            return new TransactionSimulationResult(existing.get(), true);
        }

        var resolved = scenarioResolver.resolve(new ScenarioQuery(
                command.provider(), command.operation(), command.reference(), command.attributes()
        ));
        SimulationBehavior behavior = resolved.behavior();
        Optional<TransactionStatus> scheduledCompletion = behavior.scheduledCompletion();
        try (BoundedDelayExecutor.Hold hold = delayExecutor.reserve(behavior.delay())) {
            if (scheduledCompletion.isPresent()) {
                completionScheduler.ensureCapacity();
            }
            Optional<TransactionStatus> initialStatus = behavior.initialStatus();
            SimulatedTransaction transaction = null;
            if (initialStatus.isPresent()) {
                Instant now = clock.instant();
                transaction = new SimulatedTransaction(
                        key, command.operation(), command.amount(), command.attributes(), initialStatus.get(), now, now
                );
                Optional<SimulatedTransaction> concurrent = transactionStore.saveIfAbsent(transaction);
                if (concurrent.isPresent()) {
                    telemetry.duplicate(command.provider(), command.operation());
                    return new TransactionSimulationResult(concurrent.get(), true);
                }
                if (scheduledCompletion.isPresent()) {
                    completionScheduler.schedule(key, scheduledCompletion.get(), behavior.transitionDelay());
                }
            }
            telemetry.selected(command.provider(), command.operation(), resolved);
            hold.await();
            if (behavior.outcome() == SimulationOutcome.TIMEOUT) {
                throw new SimulatedTimeoutException(command.provider(), command.operation());
            }
            return new TransactionSimulationResult(transaction, false);
        }
    }

    public Optional<SimulatedTransaction> find(String provider, String reference) {
        return transactionStore.find(new TransactionKey(provider, reference));
    }
}
