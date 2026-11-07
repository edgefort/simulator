package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.config.SimulationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileCatalog {

    private final Map<String, SimulationBehavior> profiles;
    private final String defaultProfile;

    public ProfileCatalog(SimulationProperties properties) {
        Duration maximumDelay = properties.getLimits().getMaxDelay();
        Map<String, SimulationBehavior> behaviors = new LinkedHashMap<>();
        properties.getProfiles().forEach((name, profile) -> {
            validateDuration(name, "delay", profile.getDelay(), maximumDelay);
            validateDuration(name, "transition-delay", profile.getTransitionDelay(), maximumDelay);
            behaviors.put(name, new SimulationBehavior(
                    name,
                    profile.getOutcome(),
                    profile.getDelay(),
                    profile.getCompletesAs(),
                    profile.getTransitionDelay(),
                    profile.isReceived()
            ));
        });
        this.profiles = Map.copyOf(behaviors);
        this.defaultProfile = properties.getDefaultProfile();
    }

    public SimulationBehavior get(String name) {
        SimulationBehavior behavior = profiles.get(name);
        if (behavior == null) {
            throw new SimulationControlException("Unknown simulation profile: " + name);
        }
        return behavior;
    }

    public SimulationBehavior defaultBehavior() {
        return get(defaultProfile);
    }

    public String defaultProfile() {
        return defaultProfile;
    }

    public Map<String, SimulationBehavior> all() {
        return profiles;
    }

    private static void validateDuration(String profile, String property, Duration value, Duration maximum) {
        if (value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(
                    "simulator.profiles.%s.%s must be between zero and %s".formatted(profile, property, maximum)
            );
        }
    }
}
