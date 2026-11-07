package com.edgefort.simulator.core.scenario;

public interface ScenarioResolver {

    ResolvedBehavior resolve(ScenarioQuery query);
}
