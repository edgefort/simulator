package com.edgefort.simulator.config;

import com.edgefort.simulator.core.application.BoundedDelayExecutor;
import com.edgefort.simulator.core.application.CompletionScheduler;
import com.edgefort.simulator.core.application.HttpCallbackPublisher;
import com.edgefort.simulator.core.application.ProviderOperationSimulator;
import com.edgefort.simulator.core.application.TransferSimulationService;
import com.edgefort.simulator.core.scenario.ActiveProfileRegistry;
import com.edgefort.simulator.core.scenario.DefaultScenarioResolver;
import com.edgefort.simulator.core.scenario.InMemoryScenarioOverrideStore;
import com.edgefort.simulator.core.scenario.ScenarioResolver;
import com.edgefort.simulator.core.state.InMemoryTransactionStore;
import com.edgefort.simulator.core.state.TransactionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class SimulatorConfiguration {

    @Bean
    Clock simulatorClock() {
        return Clock.systemUTC();
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
    InMemoryScenarioOverrideStore scenarioOverrideStore(Clock clock, SimulationProperties properties) {
        return new InMemoryScenarioOverrideStore(
                clock,
                properties.getLimits().getOverrideTtl(),
                properties.getLimits().getMaxOverrides()
        );
    }

    @Bean
    ActiveProfileRegistry activeProfileRegistry(SimulationProperties properties) {
        Duration maximumDelay = properties.getLimits().getMaxDelay();
        properties.getProfiles().forEach((name, profile) -> {
            validateDuration(name, "delay", profile.getDelay(), maximumDelay);
            validateDuration(name, "transition-delay", profile.getTransitionDelay(), maximumDelay);
        });
        return new ActiveProfileRegistry(properties);
    }

    @Bean
    ScenarioResolver scenarioResolver(
            ActiveProfileRegistry profileRegistry,
            InMemoryScenarioOverrideStore overrideStore
    ) {
        return new DefaultScenarioResolver(profileRegistry, overrideStore);
    }

    @Bean
    BoundedDelayExecutor boundedDelayExecutor(SimulationProperties properties) {
        return new BoundedDelayExecutor(
                properties.getLimits().getMaxHeldRequests(),
                properties.getLimits().getMaxDelay()
        );
    }

    @Bean
    ProviderOperationSimulator providerOperationSimulator(
            ScenarioResolver scenarioResolver,
            BoundedDelayExecutor delayExecutor
    ) {
        return new ProviderOperationSimulator(scenarioResolver, delayExecutor);
    }

    @Bean
    HttpCallbackPublisher callbackPublisher(SimulationProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        return new HttpCallbackPublisher(
                RestClient.builder().requestFactory(requestFactory).build(),
                properties.getAllowedCallbackHosts()
        );
    }

    @Bean
    CompletionScheduler completionScheduler(
            TransactionStore transactionStore,
            HttpCallbackPublisher callbackPublisher,
            Clock clock,
            SimulationProperties properties
    ) {
        return new CompletionScheduler(
                transactionStore,
                callbackPublisher,
                clock,
                properties.getLimits().getSchedulerThreads(),
                properties.getLimits().getMaxTransactions()
        );
    }

    @Bean
    TransferSimulationService transferSimulationService(
            ScenarioResolver scenarioResolver,
            TransactionStore transactionStore,
            BoundedDelayExecutor delayExecutor,
            CompletionScheduler completionScheduler,
            Clock clock
    ) {
        return new TransferSimulationService(
                scenarioResolver,
                transactionStore,
                delayExecutor,
                completionScheduler,
                clock
        );
    }

    private void validateDuration(String profile, String property, Duration value, Duration maximum) {
        if (value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(
                    "simulator.profiles.%s.%s must be between zero and %s".formatted(profile, property, maximum)
            );
        }
    }
}