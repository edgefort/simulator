package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.core.state.TransactionStatus;
import com.edgefort.simulator.core.state.TransactionStore;
import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class CompletionScheduler {

    private final TransactionStore transactionStore;
    private final CallbackPublisher callbackPublisher;
    private final Clock clock;
    private final ScheduledThreadPoolExecutor executor;
    private final int maximumQueuedTasks;
    private final AtomicLong generation = new AtomicLong();

    public CompletionScheduler(
            TransactionStore transactionStore,
            CallbackPublisher callbackPublisher,
            Clock clock,
            int threadCount,
            int maximumQueuedTasks
    ) {
        this.transactionStore = transactionStore;
        this.callbackPublisher = callbackPublisher;
        this.clock = clock;
        this.executor = new ScheduledThreadPoolExecutor(threadCount);
        this.executor.setRemoveOnCancelPolicy(true);
        this.maximumQueuedTasks = maximumQueuedTasks;
    }

    public synchronized void scheduleSuccess(SimulatedTransaction pending, URI callbackUrl, Duration delay) {
        if (executor.getQueue().size() >= maximumQueuedTasks) {
            throw new SimulationCapacityException("Maximum queued completion events reached");
        }
        long scheduledGeneration = generation.get();
        executor.schedule(() -> {
            synchronized (this) {
                if (scheduledGeneration != generation.get()) {
                    return;
                }
                SimulatedTransaction completed = pending.withStatus(TransactionStatus.SUCCESS, clock.instant());
                transactionStore.save(completed);
                if (callbackUrl != null) {
                    callbackPublisher.publish(callbackUrl, completed);
                }
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void validateCallbackUrl(URI callbackUrl) {
        callbackPublisher.validate(callbackUrl);
    }

    public synchronized void reset() {
        generation.incrementAndGet();
        executor.getQueue().clear();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}