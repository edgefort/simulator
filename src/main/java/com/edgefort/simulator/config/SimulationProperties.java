package com.edgefort.simulator.config;

import com.edgefort.simulator.core.scenario.CompletionStatus;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties("simulator")
public class SimulationProperties {

    @NotBlank
    private String defaultProfile = "happy-path";

    @NotBlank
    private String adminToken = "simulator";

    @Valid
    @NotNull
    private Limits limits = new Limits();

    @NotEmpty
    private Map<String, @Valid Profile> profiles = new LinkedHashMap<>();

    @NotEmpty
    private Map<String, @Valid Provider> providers = new LinkedHashMap<>();

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
        private int maxRules = 100;

        @Min(1)
        private int maxHeldRequests = 20;

        @Min(1)
        private int schedulerThreads = 2;

        @NotNull
        private Duration transactionTtl = Duration.ofMinutes(15);

        @NotNull
        private Duration overrideTtl = Duration.ofMinutes(5);

        @NotNull
        private Duration maxDelay = Duration.ofMinutes(2);

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

        public int getMaxRules() {
            return maxRules;
        }

        public void setMaxRules(int maxRules) {
            this.maxRules = maxRules;
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

    /**
     * A named provider behavior. Profiles never add fields, headers, or codes to a provider contract; each provider
     * maps the outcome to its own documented response.
     */
    public static class Profile {

        @NotNull
        private SimulationOutcome outcome = SimulationOutcome.SUCCESS;

        /** Time the request is held before the response is written, or before the connection is dropped. */
        @NotNull
        private Duration delay = Duration.ZERO;

        /** Final status of a pending or timed-out transaction. {@code never} keeps it pending. */
        @NotNull
        private CompletionStatus completesAs = CompletionStatus.SUCCESS;

        /** Time from receipt until a pending or timed-out transaction reaches {@link #completesAs}. */
        @NotNull
        private Duration transitionDelay = Duration.ZERO;

        /** For {@code timeout}: whether the provider recorded the request before the client stopped waiting. */
        private boolean received = true;

        public SimulationOutcome getOutcome() {
            return outcome;
        }

        public void setOutcome(SimulationOutcome outcome) {
            this.outcome = outcome;
        }

        public Duration getDelay() {
            return delay;
        }

        public void setDelay(Duration delay) {
            this.delay = delay;
        }

        public CompletionStatus getCompletesAs() {
            return completesAs;
        }

        public void setCompletesAs(CompletionStatus completesAs) {
            this.completesAs = completesAs;
        }

        public Duration getTransitionDelay() {
            return transitionDelay;
        }

        public void setTransitionDelay(Duration transitionDelay) {
            this.transitionDelay = transitionDelay;
        }

        public boolean isReceived() {
            return received;
        }

        public void setReceived(boolean received) {
            this.received = received;
        }
    }

    public static class Provider {

        private boolean enabled = true;

        @NotBlank
        private String specificationVersion = "unspecified";

        @NotBlank
        private String securityMode = "unspecified";

        /** Optional prefix mounted before the provider's specification paths, for example {@code /onafriq}. */
        @Pattern(regexp = "^$|^/[A-Za-z0-9._~-]+(/[A-Za-z0-9._~-]+)*$",
                message = "base-path must be empty or start with '/' and must not end with '/'")
        private String basePath = "";

        /** Profile applied to every operation of this provider that can express it. */
        private String profile;

        /** Operation-specific profiles keyed by provider operation name. */
        private Map<String, String> operationProfiles = new LinkedHashMap<>();

        /** Test-data rules that select a profile from request field values defined by the provider specification. */
        private List<@Valid Rule> rules = new ArrayList<>();

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

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public Map<String, String> getOperationProfiles() {
            return operationProfiles;
        }

        public void setOperationProfiles(Map<String, String> operationProfiles) {
            this.operationProfiles = operationProfiles;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }
    }

    public static class Rule {

        private String operation;

        @NotBlank
        private String field;

        @NotNull
        private String equals;

        @NotBlank
        private String profile;

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getEquals() {
            return equals;
        }

        public void setEquals(String equals) {
            this.equals = equals;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }
    }
}
