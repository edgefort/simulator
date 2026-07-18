package com.edgefort.simulator.core.state;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTransactionStoreTest {

    @Test
    void keepsProviderReferencesIsolatedAndCanBeReset() {
        MutableClock clock = new MutableClock();
        InMemoryTransactionStore store = new InMemoryTransactionStore(clock, Duration.ofMinutes(5), 10);
        store.save(transaction("nibss-nip", "same-reference", clock.instant()));
        store.save(transaction("interswitch-transfer", "same-reference", clock.instant()));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.find(new TransactionKey("nibss-nip", "same-reference"))).isPresent();
        assertThat(store.find(new TransactionKey("interswitch-transfer", "same-reference"))).isPresent();

        store.reset();

        assertThat(store.size()).isZero();
    }

    @Test
    void expiresEntriesAfterTheConfiguredTtl() {
        MutableClock clock = new MutableClock();
        InMemoryTransactionStore store = new InMemoryTransactionStore(clock, Duration.ofSeconds(30), 10);
        store.save(transaction("nibss-nip", "reference-1", clock.instant()));

        clock.advance(Duration.ofSeconds(31));

        assertThat(store.find(new TransactionKey("nibss-nip", "reference-1"))).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void evictsTheOldestEntryWhenTheBoundIsReached() {
        MutableClock clock = new MutableClock();
        InMemoryTransactionStore store = new InMemoryTransactionStore(clock, Duration.ofMinutes(5), 2);
        store.save(transaction("nibss-nip", "reference-1", clock.instant()));
        clock.advance(Duration.ofSeconds(1));
        store.save(transaction("nibss-nip", "reference-2", clock.instant()));
        clock.advance(Duration.ofSeconds(1));
        store.save(transaction("nibss-nip", "reference-3", clock.instant()));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.find(new TransactionKey("nibss-nip", "reference-1"))).isEmpty();
        assertThat(store.find(new TransactionKey("nibss-nip", "reference-2"))).isPresent();
        assertThat(store.find(new TransactionKey("nibss-nip", "reference-3"))).isPresent();
    }

    @Test
    void atomicallyAllowsOnlyOneConcurrentCreateForAProviderReference() throws Exception {
        MutableClock clock = new MutableClock();
        InMemoryTransactionStore store = new InMemoryTransactionStore(clock, Duration.ofMinutes(5), 10);
        SimulatedTransaction transaction = transaction("nibss-nip", "concurrent-reference", clock.instant());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> creates = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        return store.saveIfAbsent(transaction).isEmpty();
                    }))
                    .toList();

            start.countDown();

            long successfulCreates = 0;
            for (Future<Boolean> create : creates) {
                if (create.get()) {
                    successfulCreates++;
                }
            }
            assertThat(successfulCreates).isEqualTo(1);
            assertThat(store.size()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private SimulatedTransaction transaction(String provider, String reference, Instant now) {
        return new SimulatedTransaction(
                new TransactionKey(provider, reference),
                new BigDecimal("100.00"),
                TransactionStatus.SUCCESS,
                now,
                now
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}