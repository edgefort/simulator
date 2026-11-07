package com.edgefort.simulator.admin.api;

import com.edgefort.simulator.core.application.CompletionScheduler;
import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.ProviderRegistry;
import com.edgefort.simulator.core.provider.SimulatedProvider;
import com.edgefort.simulator.core.scenario.CompletionStatus;
import com.edgefort.simulator.core.scenario.ProfileAssignments;
import com.edgefort.simulator.core.scenario.ProfileCatalog;
import com.edgefort.simulator.core.scenario.ScenarioOverride;
import com.edgefort.simulator.core.scenario.ScenarioOverrideStore;
import com.edgefort.simulator.core.scenario.ScenarioRule;
import com.edgefort.simulator.core.scenario.ScenarioRuleStore;
import com.edgefort.simulator.core.scenario.SimulationBehavior;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.core.state.TransactionKey;
import com.edgefort.simulator.core.state.TransactionStatus;
import com.edgefort.simulator.core.state.TransactionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Simulator control plane. Provider requests never carry simulator parameters; every behavior choice is made here.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Administration", description = "Select provider behavior and inspect or reset simulator state")
@SecurityRequirement(name = "AdminToken")
public class AdminController {

    private final ProviderRegistry registry;
    private final ProfileCatalog catalog;
    private final ProfileAssignments assignments;
    private final ScenarioOverrideStore overrides;
    private final ScenarioRuleStore rules;
    private final TransactionStore transactionStore;
    private final CompletionScheduler completionScheduler;

    public AdminController(
            ProviderRegistry registry,
            ProfileCatalog catalog,
            ProfileAssignments assignments,
            ScenarioOverrideStore overrides,
            ScenarioRuleStore rules,
            TransactionStore transactionStore,
            CompletionScheduler completionScheduler
    ) {
        this.registry = registry;
        this.catalog = catalog;
        this.assignments = assignments;
        this.overrides = overrides;
        this.rules = rules;
        this.transactionStore = transactionStore;
        this.completionScheduler = completionScheduler;
    }

    @GetMapping("/providers")
    @Operation(summary = "List providers, their operations, supported outcomes, and assigned profiles")
    List<ProviderMetadata> providers() {
        return registry.providers().stream().map(this::metadata).toList();
    }

    @GetMapping("/profiles")
    @Operation(summary = "List the named behaviors that can be assigned to providers, operations, overrides, and rules")
    ProfilesResponse profiles() {
        return new ProfilesResponse(
                catalog.defaultProfile(),
                catalog.all().values().stream()
                        .sorted(java.util.Comparator.comparing(SimulationBehavior::profile))
                        .map(ProfileMetadata::from)
                        .toList()
        );
    }

    @PutMapping("/providers/{provider}/profile")
    @Operation(summary = "Apply a profile to every operation of a provider that can express it")
    ResponseEntity<Void> assignProviderProfile(
            @PathVariable String provider,
            @Valid @RequestBody ProfileAssignment request
    ) {
        assignments.assignProvider(provider, request.profile());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/providers/{provider}/profile")
    ResponseEntity<Void> clearProviderProfile(@PathVariable String provider) {
        assignments.clearProvider(provider);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/providers/{provider}/operations/{operation}/profile")
    @Operation(summary = "Apply a profile to one provider operation")
    ResponseEntity<Void> assignOperationProfile(
            @PathVariable String provider,
            @PathVariable String operation,
            @Valid @RequestBody ProfileAssignment request
    ) {
        assignments.assignOperation(provider, operation, request.profile());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/providers/{provider}/operations/{operation}/profile")
    ResponseEntity<Void> clearOperationProfile(@PathVariable String provider, @PathVariable String operation) {
        assignments.clearOperation(provider, operation);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/overrides")
    @Operation(summary = "Apply a profile to one provider reference, or to the next requests of a provider operation")
    ResponseEntity<ScenarioOverride> addOverride(@Valid @RequestBody OverrideRequest request) {
        ScenarioOverride override = overrides.add(
                request.provider(),
                request.operation(),
                request.reference(),
                request.profile(),
                request.count() == null ? 1 : request.count()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(override);
    }

    @GetMapping("/overrides")
    List<ScenarioOverride> listOverrides() {
        return overrides.list();
    }

    @DeleteMapping("/overrides/{id}")
    ResponseEntity<Void> removeOverride(@PathVariable String id) {
        return overrides.remove(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/rules")
    @Operation(summary = "Select a profile whenever a specification-defined request field equals a value")
    ResponseEntity<ScenarioRule> addRule(@Valid @RequestBody RuleRequest request) {
        ScenarioRule rule = rules.add(
                request.provider(), request.operation(), request.field(), request.equals(), request.profile()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @GetMapping("/rules")
    List<ScenarioRule> listRules() {
        return rules.list();
    }

    @DeleteMapping("/rules/{id}")
    ResponseEntity<Void> removeRule(@PathVariable String id) {
        return rules.remove(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/transactions/{provider}/{reference}")
    ResponseEntity<SimulatedTransaction> transaction(
            @PathVariable String provider,
            @PathVariable String reference
    ) {
        registry.provider(provider);
        return transactionStore.find(new TransactionKey(provider, reference))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/state/reset")
    @Operation(summary = "Clear transactions, overrides, and queued transitions; restore configured profiles and rules")
    ResponseEntity<Void> reset() {
        completionScheduler.reset();
        transactionStore.reset();
        overrides.reset();
        rules.reset();
        assignments.reset();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/state")
    ResponseEntity<Void> resetAlias() {
        return reset();
    }

    private ProviderMetadata metadata(SimulatedProvider provider) {
        var settings = registry.settings(provider.id());
        return new ProviderMetadata(
                provider.id(),
                settings.isEnabled(),
                settings.getSpecificationVersion(),
                settings.getSecurityMode(),
                settings.getBasePath(),
                assignments.providerProfile(provider.id()).orElse(null),
                assignments.operationProfiles(provider.id()),
                provider.operations().stream()
                        .sorted(java.util.Comparator.comparing(ProviderOperation::name))
                        .map(OperationMetadata::from)
                        .toList()
        );
    }

    record ProviderMetadata(
            String id,
            boolean enabled,
            String specificationVersion,
            String securityMode,
            String basePath,
            String profile,
            Map<String, String> operationProfiles,
            List<OperationMetadata> operations
    ) {
    }

    record OperationMetadata(
            String name,
            boolean transactional,
            Set<SimulationOutcome> outcomes,
            Set<TransactionStatus> queryStatuses,
            Set<String> matchFields
    ) {

        static OperationMetadata from(ProviderOperation operation) {
            return new OperationMetadata(
                    operation.name(),
                    operation.transactional(),
                    new TreeSet<>(operation.outcomes()),
                    new TreeSet<>(operation.queryStatuses()),
                    new TreeSet<>(operation.matchFields())
            );
        }
    }

    record ProfilesResponse(String defaultProfile, List<ProfileMetadata> profiles) {
    }

    record ProfileMetadata(
            String name,
            SimulationOutcome outcome,
            Duration delay,
            CompletionStatus completesAs,
            Duration transitionDelay,
            boolean received
    ) {

        static ProfileMetadata from(SimulationBehavior behavior) {
            return new ProfileMetadata(
                    behavior.profile(),
                    behavior.outcome(),
                    behavior.delay(),
                    behavior.completesAs(),
                    behavior.transitionDelay(),
                    behavior.received()
            );
        }
    }

    record ProfileAssignment(@NotBlank String profile) {
    }

    record OverrideRequest(
            @NotBlank String provider,
            String operation,
            String reference,
            @NotBlank String profile,
            @Min(1) Integer count
    ) {
    }

    record RuleRequest(
            @NotBlank String provider,
            String operation,
            @NotBlank String field,
            @NotNull String equals,
            @NotBlank String profile
    ) {
    }
}
