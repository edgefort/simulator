package com.edgefort.simulator.core.application;

import com.edgefort.simulator.core.state.SimulatedTransaction;

import java.net.URI;

public interface CallbackPublisher {

    void validate(URI callbackUrl);

    void publish(URI callbackUrl, SimulatedTransaction transaction);
}