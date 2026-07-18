package com.edgefort.simulator.core.scenario;

public class UnknownScenarioException extends RuntimeException {

    public UnknownScenarioException(String scenario) {
        super("Unknown simulation scenario: " + scenario);
    }
}