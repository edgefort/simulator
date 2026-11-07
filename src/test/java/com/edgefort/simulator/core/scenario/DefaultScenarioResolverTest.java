package com.edgefort.simulator.core.scenario;

import com.edgefort.simulator.config.SimulationProperties;
import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.ProviderRegistry;
import com.edgefort.simulator.core.provider.SimulatedProvider;
import com.edgefort.simulator.core.state.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultScenarioResolverTest {

    private static final String PROVIDER = "test-provider";

    private SimulationProperties properties;
    private ProfileCatalog catalog;
    private ProviderRegistry registry;
    private ProfileAssignments assignments;
    private ScenarioOverrideStore overrides;
    private ScenarioRuleStore rules;
    private DefaultScenarioResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new SimulationProperties();
        properties.setDefaultProfile("happy-path");
        Map<String, SimulationProperties.Profile> profiles = new LinkedHashMap<>();
        profiles.put("happy-path", profile(SimulationOutcome.SUCCESS, CompletionStatus.SUCCESS));
        profiles.put("failure", profile(SimulationOutcome.FAILURE, CompletionStatus.SUCCESS));
        profiles.put("pending", profile(SimulationOutcome.PENDING, CompletionStatus.SUCCESS));
        profiles.put("pending-then-reversed", profile(SimulationOutcome.PENDING, CompletionStatus.REVERSED));
        profiles.put("timeout", profile(SimulationOutcome.TIMEOUT, CompletionStatus.SUCCESS));
        properties.setProfiles(profiles);
        SimulationProperties.Provider settings = new SimulationProperties.Provider();
        properties.setProviders(new LinkedHashMap<>(Map.of(PROVIDER, settings)));
        build();
    }

    @Test
    void precedenceIsReferenceOverrideThenOperationOverrideThenRuleThenOperationThenProviderProfile() {
        assignments.assignProvider(PROVIDER, "timeout");
        assignments.assignOperation(PROVIDER, "transfer", "failure");
        rules.add(PROVIDER, "transfer", "AccountNumber", "0000000001", "pending");
        overrides.add(PROVIDER, "transfer", null, "timeout", 1);
        overrides.add(PROVIDER, null, "reference-1", "happy-path", 1);
        ScenarioQuery query = transfer("reference-1", "0000000001");

        assertResolved(query, "happy-path", ScenarioSource.REFERENCE_OVERRIDE);
        assertResolved(query, "timeout", ScenarioSource.OPERATION_OVERRIDE);
        assertResolved(query, "pending", ScenarioSource.RULE);
        assertResolved(transfer("reference-1", "other-account"), "failure", ScenarioSource.OPERATION_PROFILE);
        assertResolved(enquiry(), "timeout", ScenarioSource.PROVIDER_PROFILE);

        assignments.clearProvider(PROVIDER);
        assertResolved(enquiry(), "happy-path", ScenarioSource.DEFAULT_PROFILE);
    }

    @Test
    void providerProfileOnlyAppliesToOperationsThatCanExpressIt() {
        assignments.assignProvider(PROVIDER, "pending");

        assertResolved(transfer("reference-1", "account"), "pending", ScenarioSource.PROVIDER_PROFILE);
        assertResolved(enquiry(), "happy-path", ScenarioSource.DEFAULT_PROFILE);
    }

    @Test
    void operationOverrideIsConsumedOnlyByOperationsThatCanExpressItAndHonoursCount() {
        overrides.add(PROVIDER, null, null, "pending", 2);

        assertResolved(enquiry(), "happy-path", ScenarioSource.DEFAULT_PROFILE);
        assertResolved(transfer("reference-1", "account"), "pending", ScenarioSource.OPERATION_OVERRIDE);
        assertResolved(transfer("reference-2", "account"), "pending", ScenarioSource.OPERATION_OVERRIDE);
        assertResolved(transfer("reference-3", "account"), "happy-path", ScenarioSource.DEFAULT_PROFILE);
        assertThat(overrides.list()).isEmpty();
    }

    @Test
    void rejectsProfilesTheSpecificationCannotExpress() {
        assertThatThrownBy(() -> assignments.assignOperation(PROVIDER, "name-enquiry", "pending"))
                .isInstanceOf(SimulationControlException.class)
                .hasMessageContaining("no pending response");
        assertThatThrownBy(() -> overrides.add(PROVIDER, "transfer", null, "pending-then-reversed", 1))
                .isInstanceOf(SimulationControlException.class)
                .hasMessageContaining("REVERSED");
        assertThatThrownBy(() -> rules.add(PROVIDER, "name-enquiry", "Amount", "1", "failure"))
                .isInstanceOf(SimulationControlException.class)
                .hasMessageContaining("not a match field");
        assertThatThrownBy(() -> assignments.assignOperation(PROVIDER, "unknown", "failure"))
                .isInstanceOf(SimulationControlException.class)
                .hasMessageContaining("Unknown operation");
    }

    @Test
    void resetRestoresConfiguredAssignmentsAndRules() {
        SimulationProperties.Rule rule = new SimulationProperties.Rule();
        rule.setOperation("transfer");
        rule.setField("AccountNumber");
        rule.setEquals("0000000009");
        rule.setProfile("failure");
        properties.getProviders().get(PROVIDER).getRules().add(rule);
        properties.getProviders().get(PROVIDER).getOperationProfiles().put("name-enquiry", "timeout");
        build();

        assignments.assignOperation(PROVIDER, "name-enquiry", "failure");
        rules.list().forEach(existing -> rules.remove(existing.id()));
        assertResolved(transfer("reference-1", "0000000009"), "happy-path", ScenarioSource.DEFAULT_PROFILE);

        assignments.reset();
        rules.reset();

        assertResolved(enquiry(), "timeout", ScenarioSource.OPERATION_PROFILE);
        assertResolved(transfer("reference-1", "0000000009"), "failure", ScenarioSource.RULE);
    }

    @Test
    void timeoutRecordsThePendingTransactionOnlyWhenTheProviderReceivedIt() {
        SimulationBehavior received = new SimulationBehavior(
                "t", SimulationOutcome.TIMEOUT, Duration.ofSeconds(1), CompletionStatus.FAILED, Duration.ofSeconds(1), true
        );
        SimulationBehavior immediate = new SimulationBehavior(
                "t", SimulationOutcome.TIMEOUT, Duration.ofSeconds(1), CompletionStatus.FAILED, Duration.ZERO, true
        );
        SimulationBehavior notReceived = new SimulationBehavior(
                "t", SimulationOutcome.TIMEOUT, Duration.ofSeconds(1), CompletionStatus.SUCCESS, Duration.ZERO, false
        );

        assertThat(received.initialStatus()).contains(TransactionStatus.PENDING);
        assertThat(received.scheduledCompletion()).contains(TransactionStatus.FAILED);
        assertThat(immediate.initialStatus()).contains(TransactionStatus.FAILED);
        assertThat(immediate.scheduledCompletion()).isEmpty();
        assertThat(notReceived.initialStatus()).isEmpty();
        assertThat(notReceived.observableStatuses()).isEmpty();
    }

    private void build() {
        registry = new ProviderRegistry(List.of(new TestProvider()), properties);
        catalog = new ProfileCatalog(properties);
        assignments = new ProfileAssignments(properties, catalog, registry);
        overrides = new ScenarioOverrideStore(Clock.systemUTC(), Duration.ofMinutes(5), 10, catalog, registry);
        rules = new ScenarioRuleStore(properties, catalog, registry);
        resolver = new DefaultScenarioResolver(catalog, assignments, overrides, rules, registry);
    }

    private void assertResolved(ScenarioQuery query, String profile, ScenarioSource source) {
        ResolvedBehavior resolved = resolver.resolve(query);
        assertThat(resolved.behavior().profile()).isEqualTo(profile);
        assertThat(resolved.source()).isEqualTo(source);
    }

    private ScenarioQuery transfer(String reference, String accountNumber) {
        return new ScenarioQuery(PROVIDER, "transfer", reference, Map.of("AccountNumber", accountNumber));
    }

    private ScenarioQuery enquiry() {
        return new ScenarioQuery(PROVIDER, "name-enquiry", null, Map.of("AccountNumber", "0000000001"));
    }

    private SimulationProperties.Profile profile(SimulationOutcome outcome, CompletionStatus completesAs) {
        SimulationProperties.Profile profile = new SimulationProperties.Profile();
        profile.setOutcome(outcome);
        profile.setCompletesAs(completesAs);
        profile.setTransitionDelay(Duration.ofMillis(20));
        return profile;
    }

    private static final class TestProvider implements SimulatedProvider {

        @Override
        public String id() {
            return PROVIDER;
        }

        @Override
        public List<ProviderOperation> operations() {
            return List.of(
                    ProviderOperation.stateless(
                            "name-enquiry", Set.of("AccountNumber"),
                            SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.TIMEOUT
                    ),
                    ProviderOperation.transactional(
                            "transfer",
                            Set.of("AccountNumber", "Amount"),
                            EnumSet.allOf(SimulationOutcome.class),
                            EnumSet.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS, TransactionStatus.FAILED)
                    )
            );
        }
    }
}
