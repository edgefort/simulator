package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.config.SimulationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultScenarioResolverTest {

    private InMemoryScenarioOverrideStore overrideStore;
    private ActiveProfileRegistry profileRegistry;
    private DefaultScenarioResolver resolver;

    @BeforeEach
    void setUp() {
        SimulationProperties properties = new SimulationProperties();
        properties.setDefaultProfile("happy-path");
        properties.setProfiles(new LinkedHashMap<>(Map.of(
                "happy-path", profile(SimulationScenario.SUCCESS, Duration.ZERO),
                "failure", profile(SimulationScenario.FAILURE, Duration.ZERO),
                "timeout", profile(SimulationScenario.TIMEOUT, Duration.ofMillis(10))
        )));
        overrideStore = new InMemoryScenarioOverrideStore(Clock.systemUTC(), Duration.ofMinutes(5), 10);
        profileRegistry = new ActiveProfileRegistry(properties);
        resolver = new DefaultScenarioResolver(profileRegistry, overrideStore);
    }

    @Test
    void usesTheActiveProfileWhenNoHigherPriorityRuleExists() {
        assertThat(resolver.resolve(query("reference-1", null)).scenario())
                .isEqualTo(SimulationScenario.SUCCESS);

        profileRegistry.activate("failure");

        assertThat(resolver.resolve(query("reference-1", null)).scenario())
                .isEqualTo(SimulationScenario.FAILURE);
    }

    @Test
    void explicitRequestScenarioTakesPriorityOverProfile() {
        assertThat(resolver.resolve(query("reference-1", "pending")).scenario())
                .isEqualTo(SimulationScenario.PENDING);
    }

    @Test
    void oneTimeOverrideHasHighestPriorityAndIsConsumed() {
        overrideStore.put(new ScenarioOverride("nibss-nip", "reference-1", SimulationScenario.TIMEOUT));

        assertThat(resolver.resolve(query("reference-1", "failure")).scenario())
                .isEqualTo(SimulationScenario.TIMEOUT);
        assertThat(resolver.resolve(query("reference-1", "failure")).scenario())
                .isEqualTo(SimulationScenario.FAILURE);
    }

    @Test
    void rejectsUnknownExplicitScenarios() {
        assertThatThrownBy(() -> resolver.resolve(query("reference-1", "not-a-scenario")))
                .isInstanceOf(UnknownScenarioException.class)
                .hasMessageContaining("not-a-scenario");
    }

    private ScenarioQuery query(String reference, String requestedScenario) {
        return new ScenarioQuery("nibss-nip", "transfer", reference, requestedScenario);
    }

    private SimulationProperties.Profile profile(SimulationScenario scenario, Duration delay) {
        SimulationProperties.Profile profile = new SimulationProperties.Profile();
        profile.setScenario(scenario);
        profile.setDelay(delay);
        profile.setTransitionDelay(Duration.ofMillis(20));
        return profile;
    }
}