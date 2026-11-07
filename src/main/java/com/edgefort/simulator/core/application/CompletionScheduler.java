package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.core.state.TransactionKey;
import com.edgefort.simulator.core.state.TransactionStatus;
import com.edgefort.simulator.core.state.TransactionStore;
import jakarta.annotation.PreDestroy;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Moves pending transactions to their final status. Reset cancels every queued transition.
 */
public class CompletionScheduler {

    private final TransactionStore transactionStore;
    private final Clock clock;
    private final ScheduledThreadPoolExecutor executor;
    private final int maximumQueuedTasks;
    private final AtomicLong generation = new AtomicLong();

    public CompletionScheduler(
            TransactionStore transactionStore,
            Clock clock,
            int threadCount,
            int maximumQueuedTasks
    ) {
        this.transactionStore = transactionStore;
        this.clock = clock;
        this.executor = new ScheduledThreadPoolExecutor(threadCount);
        this.executor.setRemoveOnCancelPolicy(true);
        this.maximumQueuedTasks = maximumQueuedTasks;
    }

    public void ensureCapacity() {
        if (executor.getQueue().size() >= maximumQueuedTasks) {
            throw new SimulationCapacityException("Maximum queued completion events reached");
        }
    }

    public synchronized void schedule(TransactionKey key, TransactionStatus finalStatus, Duration delay) {
        ensureCapacity();
        long scheduledGeneration = generation.get();
        executor.schedule(() -> complete(key, finalStatus, scheduledGeneration), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized void reset() {
        generation.incrementAndGet();
        executor.getQueue().clear();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private synchronized void complete(TransactionKey key, TransactionStatus finalStatus, long scheduledGeneration) {
        if (scheduledGeneration != generation.get()) {
            return;
        }
        transactionStore.find(key)
                .filter(transaction -> transaction.status() == TransactionStatus.PENDING)
                .map(transaction -> transaction.withStatus(finalStatus, clock.instant()))
                .ifPresent(transactionStore::save);
    }
}
