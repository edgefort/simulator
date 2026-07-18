package com.edgefort.simulator.core.state;

import java.util.Optional;

public interface TransactionStore {

    Optional<SimulatedTransaction> find(TransactionKey key);

    SimulatedTransaction save(SimulatedTransaction transaction);

    Optional<SimulatedTransaction> saveIfAbsent(SimulatedTransaction transaction);

    int size();

    void reset();
}