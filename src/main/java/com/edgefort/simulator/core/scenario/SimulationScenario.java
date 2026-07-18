package com.edgefort.simulator.core.scenario;

import java.util.Locale;

public enum SimulationScenario {
    SUCCESS,
    FAILURE,
    PENDING,
    DELAYED_SUCCESS,
    TIMEOUT,
    EVENT_TIMEOUT,
    LATE_EVENT;

    public static SimulationScenario fromExternalValue(String value) {
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new UnknownScenarioException(value);
        }
    }
}