package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.config.SimulationProperties;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ActiveProfileRegistry {

    private final Map<String, SimulationProperties.Profile> profiles;
    private final AtomicReference<String> activeProfile;

    public ActiveProfileRegistry(SimulationProperties properties) {
        this.profiles = Map.copyOf(properties.getProfiles());
        this.activeProfile = new AtomicReference<>(properties.getDefaultProfile());
    }

    public void activate(String profile) {
        if (!profiles.containsKey(profile)) {
            throw new UnknownScenarioException(profile);
        }
        activeProfile.set(profile);
    }

    public String activeProfile() {
        return activeProfile.get();
    }

    public SimulationBehavior activeBehavior() {
        return toBehavior(profiles.get(activeProfile()));
    }

    public SimulationBehavior behaviorFor(SimulationScenario scenario) {
        return profiles.values().stream()
                .filter(profile -> profile.getScenario() == scenario)
                .findFirst()
                .map(this::toBehavior)
                .orElseGet(() -> new SimulationBehavior(scenario, java.time.Duration.ZERO, java.time.Duration.ZERO));
    }

    public Map<String, SimulationProperties.Profile> profiles() {
        return profiles;
    }

    private SimulationBehavior toBehavior(SimulationProperties.Profile profile) {
        return new SimulationBehavior(profile.getScenario(), profile.getDelay(), profile.getTransitionDelay());
    }
}