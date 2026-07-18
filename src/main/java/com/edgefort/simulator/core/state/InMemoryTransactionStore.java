package com.edgefort.simulator.core.state;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTransactionStore implements TransactionStore {

    private final ConcurrentHashMap<TransactionKey, SimulatedTransaction> transactions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;
    private final int maximumSize;

    public InMemoryTransactionStore(Clock clock, Duration ttl, int maximumSize) {
        this.clock = clock;
        this.ttl = ttl;
        this.maximumSize = maximumSize;
    }

    @Override
    public synchronized Optional<SimulatedTransaction> find(TransactionKey key) {
        purgeExpired();
        return Optional.ofNullable(transactions.get(key));
    }

    @Override
    public synchronized SimulatedTransaction save(SimulatedTransaction transaction) {
        purgeExpired();
        ensureCapacityFor(transaction.key());
        transactions.put(transaction.key(), transaction);
        return transaction;
    }

    @Override
    public synchronized Optional<SimulatedTransaction> saveIfAbsent(SimulatedTransaction transaction) {
        purgeExpired();
        SimulatedTransaction existing = transactions.get(transaction.key());
        if (existing != null) {
            return Optional.of(existing);
        }
        ensureCapacityFor(transaction.key());
        transactions.put(transaction.key(), transaction);
        return Optional.empty();
    }

    @Override
    public synchronized int size() {
        purgeExpired();
        return transactions.size();
    }

    @Override
    public void reset() {
        transactions.clear();
    }

    private void purgeExpired() {
        Instant expiryBoundary = clock.instant().minus(ttl);
        transactions.entrySet().removeIf(entry -> entry.getValue().updatedAt().isBefore(expiryBoundary));
    }

    private void ensureCapacityFor(TransactionKey key) {
        if (!transactions.containsKey(key) && transactions.size() >= maximumSize) {
            transactions.values().stream()
                    .min(Comparator.comparing(SimulatedTransaction::updatedAt))
                    .map(SimulatedTransaction::key)
                    .ifPresent(transactions::remove);
        }
    }
}