package com.edgefort.simulator.core.scenario;

/**
 * Where a resolved behavior came from, in precedence order.
 */
public enum ScenarioSource {
    REFERENCE_OVERRIDE,
    OPERATION_OVERRIDE,
    RULE,
    OPERATION_PROFILE,
    PROVIDER_PROFILE,
    DEFAULT_PROFILE
}
