package com.edgefort.simulator.config;

import com.edgefort.simulator.core.application.BoundedDelayExecutor;
import com.edgefort.simulator.core.application.CompletionScheduler;
import com.edgefort.simulator.core.application.ProviderOperationSimulator;
import com.edgefort.simulator.core.application.SimulationTelemetry;
import com.edgefort.simulator.core.application.TransactionSimulationService;
import com.edgefort.simulator.core.provider.ProviderRegistry;
import com.edgefort.simulator.core.provider.SimulatedProvider;
import com.edgefort.simulator.core.scenario.DefaultScenarioResolver;
import com.edgefort.simulator.core.scenario.ProfileAssignments;
import com.edgefort.simulator.core.scenario.ProfileCatalog;
import com.edgefort.simulator.core.scenario.ScenarioOverrideStore;
import com.edgefort.simulator.core.scenario.ScenarioResolver;
import com.edgefort.simulator.core.scenario.ScenarioRuleStore;
import com.edgefort.simulator.core.state.InMemoryTransactionStore;
import com.edgefort.simulator.core.state.TransactionStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
public class SimulatorConfiguration {

    @Bean
    Clock simulatorClock() {
        return Clock.systemUTC();
    }

    @Bean
    ProviderRegistry providerRegistry(List<SimulatedProvider> providers, SimulationProperties properties) {
        return new ProviderRegistry(providers, properties);
    }

    @Bean
    ProfileCatalog profileCatalog(SimulationProperties properties, ProviderRegistry registry) {
        ProfileCatalog catalog = new ProfileCatalog(properties);
        registry.providers().forEach(provider -> provider.operations().forEach(operation ->
                operation.unsupportedReason(catalog.defaultBehavior()).ifPresent(reason -> {
                    throw new IllegalStateException("simulator.default-profile '%s' is not valid for %s %s: %s"
                            .formatted(catalog.defaultProfile(), provider.id(), operation.name(), reason));
                })
        ));
        return catalog;
    }

    @Bean
    TransactionStore transactionStore(Clock clock, SimulationProperties properties) {
        return new InMemoryTransactionStore(
                clock,
                properties.getLimits().getTransactionTtl(),
                properties.getLimits().getMaxTransactions()
        );
    }

    @Bean
    ProfileAssignments profileAssignments(
            SimulationProperties properties,
            ProfileCatalog catalog,
            ProviderRegistry registry
    ) {
        return new ProfileAssignments(properties, catalog, registry);
    }

    @Bean
    ScenarioOverrideStore scenarioOverrideStore(
            Clock clock,
            SimulationProperties properties,
            ProfileCatalog catalog,
            ProviderRegistry registry
    ) {
        return new ScenarioOverrideStore(
                clock,
                properties.getLimits().getOverrideTtl(),
                properties.getLimits().getMaxOverrides(),
                catalog,
                registry
        );
    }

    @Bean
    ScenarioRuleStore scenarioRuleStore(
            SimulationProperties properties,
            ProfileCatalog catalog,
            ProviderRegistry registry
    ) {
        return new ScenarioRuleStore(properties, catalog, registry);
    }

    @Bean
    ScenarioResolver scenarioResolver(
            ProfileCatalog catalog,
            ProfileAssignments assignments,
            ScenarioOverrideStore overrides,
            ScenarioRuleStore rules,
            ProviderRegistry registry
    ) {
        return new DefaultScenarioResolver(catalog, assignments, overrides, rules, registry);
    }

    @Bean
    BoundedDelayExecutor boundedDelayExecutor(SimulationProperties properties) {
        return new BoundedDelayExecutor(
                properties.getLimits().getMaxHeldRequests(),
                properties.getLimits().getMaxDelay()
        );
    }

    @Bean
    SimulationTelemetry simulationTelemetry(MeterRegistry meterRegistry, BoundedDelayExecutor delayExecutor) {
        return new SimulationTelemetry(meterRegistry, delayExecutor);
    }

    @Bean
    ProviderOperationSimulator providerOperationSimulator(
            ScenarioResolver scenarioResolver,
            BoundedDelayExecutor delayExecutor,
            SimulationTelemetry telemetry
    ) {
        return new ProviderOperationSimulator(scenarioResolver, delayExecutor, telemetry);
    }

    @Bean
    CompletionScheduler completionScheduler(
            TransactionStore transactionStore,
            Clock clock,
            SimulationProperties properties
    ) {
        return new CompletionScheduler(
                transactionStore,
                clock,
                properties.getLimits().getSchedulerThreads(),
                properties.getLimits().getMaxTransactions()
        );
    }

    @Bean
    TransactionSimulationService transactionSimulationService(
            ScenarioResolver scenarioResolver,
            TransactionStore transactionStore,
            BoundedDelayExecutor delayExecutor,
            CompletionScheduler completionScheduler,
            SimulationTelemetry telemetry,
            Clock clock
    ) {
        return new TransactionSimulationService(
                scenarioResolver,
                transactionStore,
                delayExecutor,
                completionScheduler,
                telemetry,
                clock
        );
    }
}
