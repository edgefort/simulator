package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.scenario.ScenarioQuery;
import com.edgefort.simulator.core.scenario.ScenarioResolver;
import com.edgefort.simulator.core.scenario.SimulationBehavior;
import com.edgefort.simulator.core.scenario.SimulationScenario;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.core.state.TransactionKey;
import com.edgefort.simulator.core.state.TransactionStatus;
import com.edgefort.simulator.core.state.TransactionStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class TransferSimulationService {

    private final ScenarioResolver scenarioResolver;
    private final TransactionStore transactionStore;
    private final BoundedDelayExecutor delayExecutor;
    private final CompletionScheduler completionScheduler;
    private final Clock clock;

    public TransferSimulationService(
            ScenarioResolver scenarioResolver,
            TransactionStore transactionStore,
            BoundedDelayExecutor delayExecutor,
            CompletionScheduler completionScheduler,
            Clock clock
    ) {
        this.scenarioResolver = scenarioResolver;
        this.transactionStore = transactionStore;
        this.delayExecutor = delayExecutor;
        this.completionScheduler = completionScheduler;
        this.clock = clock;
    }

    public TransferSimulationResult simulate(TransferCommand command, String requestedScenario) {
        TransactionKey key = new TransactionKey(command.provider(), command.reference());
        Optional<SimulatedTransaction> existing = transactionStore.find(key);
        if (existing.isPresent()) {
            return new TransferSimulationResult(
                    command.reference(), existing.get().status(), SimulationScenario.SUCCESS, true, false
            );
        }

        SimulationBehavior behavior = scenarioResolver.resolve(new ScenarioQuery(
                command.provider(), command.operation(), command.reference(), requestedScenario
        ));
        if (behavior.scenario() == SimulationScenario.DELAYED_SUCCESS
                || behavior.scenario() == SimulationScenario.TIMEOUT) {
            delayExecutor.hold(behavior.delay());
        }
        if (behavior.scenario() == SimulationScenario.TIMEOUT) {
            return new TransferSimulationResult(
                    command.reference(), TransactionStatus.PENDING, behavior.scenario(), false, true
            );
        }

        TransactionStatus status = switch (behavior.scenario()) {
            case FAILURE -> TransactionStatus.FAILED;
            case PENDING, EVENT_TIMEOUT, LATE_EVENT -> TransactionStatus.PENDING;
            case SUCCESS, DELAYED_SUCCESS -> TransactionStatus.SUCCESS;
            case TIMEOUT -> throw new IllegalStateException("Timeout was already handled");
        };
        if (status == TransactionStatus.PENDING
                && behavior.scenario() != SimulationScenario.EVENT_TIMEOUT
                && command.callbackUrl() != null) {
            completionScheduler.validateCallbackUrl(command.callbackUrl());
        }
        Instant now = clock.instant();
        SimulatedTransaction transaction = new SimulatedTransaction(
                key, command.amount(), status, now, now
        );
        Optional<SimulatedTransaction> concurrentExisting = transactionStore.saveIfAbsent(transaction);
        if (concurrentExisting.isPresent()) {
            return new TransferSimulationResult(
                    command.reference(), concurrentExisting.get().status(), behavior.scenario(), true, false
            );
        }
        if (status == TransactionStatus.PENDING && behavior.scenario() != SimulationScenario.EVENT_TIMEOUT) {
            completionScheduler.scheduleSuccess(transaction, command.callbackUrl(), behavior.transitionDelay());
        }
        return new TransferSimulationResult(
                command.reference(), status, behavior.scenario(), false, false
        );
    }

    public Optional<SimulatedTransaction> find(String provider, String reference) {
        return transactionStore.find(new TransactionKey(provider, reference));
    }
}