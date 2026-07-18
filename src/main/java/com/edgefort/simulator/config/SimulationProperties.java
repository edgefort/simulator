package com.edgefort.simulator.config;

import com.edgefort.simulator.core.scenario.SimulationScenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties("simulator")
public class SimulationProperties {

    @NotBlank
    private String defaultProfile = "happy-path";

    @NotBlank
    private String adminToken = "local-admin";

    @Valid
    @NotNull
    private Limits limits = new Limits();

    @NotEmpty
    private Map<String, @Valid Profile> profiles = new LinkedHashMap<>();

    @NotEmpty
    private Map<String, @Valid Provider> providers = new LinkedHashMap<>();

    private List<String> allowedCallbackHosts = List.of("localhost", "127.0.0.1");

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, Profile> profiles) {
        this.profiles = profiles;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }

    public List<String> getAllowedCallbackHosts() {
        return allowedCallbackHosts;
    }

    public void setAllowedCallbackHosts(List<String> allowedCallbackHosts) {
        this.allowedCallbackHosts = allowedCallbackHosts;
    }

    @AssertTrue(message = "default profile must exist in simulator.profiles")
    public boolean isDefaultProfileConfigured() {
        return profiles.containsKey(defaultProfile);
    }

    public static class Limits {

        @Min(1)
        private int maxTransactions = 1_000;

        @Min(1)
        private int maxOverrides = 100;

        @Min(1)
        private int maxHeldRequests = 20;

        @Min(1)
        private int schedulerThreads = 2;

        @NotNull
        private Duration transactionTtl = Duration.ofMinutes(15);

        @NotNull
        private Duration overrideTtl = Duration.ofMinutes(5);

        @NotNull
        private Duration maxDelay = Duration.ofSeconds(30);

        public int getMaxTransactions() {
            return maxTransactions;
        }

        public void setMaxTransactions(int maxTransactions) {
            this.maxTransactions = maxTransactions;
        }

        public int getMaxOverrides() {
            return maxOverrides;
        }

        public void setMaxOverrides(int maxOverrides) {
            this.maxOverrides = maxOverrides;
        }

        public int getMaxHeldRequests() {
            return maxHeldRequests;
        }

        public void setMaxHeldRequests(int maxHeldRequests) {
            this.maxHeldRequests = maxHeldRequests;
        }

        public int getSchedulerThreads() {
            return schedulerThreads;
        }

        public void setSchedulerThreads(int schedulerThreads) {
            this.schedulerThreads = schedulerThreads;
        }

        public Duration getTransactionTtl() {
            return transactionTtl;
        }

        public void setTransactionTtl(Duration transactionTtl) {
            this.transactionTtl = transactionTtl;
        }

        public Duration getOverrideTtl() {
            return overrideTtl;
        }

        public void setOverrideTtl(Duration overrideTtl) {
            this.overrideTtl = overrideTtl;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }
    }

    public static class Profile {

        @NotNull
        private SimulationScenario scenario = SimulationScenario.SUCCESS;

        @NotNull
        private Duration delay = Duration.ZERO;

        @NotNull
        private Duration transitionDelay = Duration.ofSeconds(1);

        public SimulationScenario getScenario() {
            return scenario;
        }

        public void setScenario(SimulationScenario scenario) {
            this.scenario = scenario;
        }

        public Duration getDelay() {
            return delay;
        }

        public void setDelay(Duration delay) {
            this.delay = delay;
        }

        public Duration getTransitionDelay() {
            return transitionDelay;
        }

        public void setTransitionDelay(Duration transitionDelay) {
            this.transitionDelay = transitionDelay;
        }
    }

    public static class Provider {

        private boolean enabled = true;

        @NotBlank
        private String specificationVersion = "provisional";

        @NotBlank
        private String securityMode = "relaxed";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSpecificationVersion() {
            return specificationVersion;
        }

        public void setSpecificationVersion(String specificationVersion) {
            this.specificationVersion = specificationVersion;
        }

        public String getSecurityMode() {
            return securityMode;
        }

        public void setSecurityMode(String securityMode) {
            this.securityMode = securityMode;
        }
    }
}