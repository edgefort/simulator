package com.edgefort.simulator.core.scenario;

public interface ScenarioResolver {

    SimulationBehavior resolve(ScenarioQuery query);
}