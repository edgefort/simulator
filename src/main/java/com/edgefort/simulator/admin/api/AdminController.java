package com.edgefort.simulator.admin.api;

import com.edgefort.simulator.config.SimulationProperties;
import com.edgefort.simulator.core.application.CompletionScheduler;
import com.edgefort.simulator.core.scenario.ActiveProfileRegistry;
import com.edgefort.simulator.core.scenario.InMemoryScenarioOverrideStore;
import com.edgefort.simulator.core.scenario.ScenarioOverride;
import com.edgefort.simulator.core.scenario.SimulationScenario;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.core.state.TransactionKey;
import com.edgefort.simulator.core.state.TransactionStore;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@Tag(name = "Administration", description = "Inspect and control simulator state and scenarios")
@SecurityRequirement(name = "AdminToken")
public class AdminController {

    private final SimulationProperties properties;
    private final ActiveProfileRegistry profileRegistry;
    private final InMemoryScenarioOverrideStore overrideStore;
    private final TransactionStore transactionStore;
    private final CompletionScheduler completionScheduler;

    public AdminController(
            SimulationProperties properties,
            ActiveProfileRegistry profileRegistry,
            InMemoryScenarioOverrideStore overrideStore,
            TransactionStore transactionStore,
            CompletionScheduler completionScheduler
    ) {
        this.properties = properties;
        this.profileRegistry = profileRegistry;
        this.overrideStore = overrideStore;
        this.transactionStore = transactionStore;
        this.completionScheduler = completionScheduler;
    }

    @GetMapping("/providers")
    List<ProviderMetadata> providers() {
        return properties.getProviders().entrySet().stream()
                .map(entry -> new ProviderMetadata(
                        entry.getKey(),
                        entry.getValue().isEnabled(),
                        entry.getValue().getSpecificationVersion(),
                        entry.getValue().getSecurityMode()
                ))
                .toList();
    }

    @GetMapping("/profiles")
    ProfilesResponse profiles() {
        return new ProfilesResponse(profileRegistry.activeProfile(), profileRegistry.profiles().keySet());
    }

    @PutMapping("/profiles/{profile}/activate")
    ResponseEntity<Void> activateProfile(@PathVariable String profile) {
        profileRegistry.activate(profile);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/overrides")
    ResponseEntity<Void> override(@Valid @RequestBody OverrideRequest request) {
        overrideStore.put(new ScenarioOverride(request.provider(), request.reference(), request.scenario()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/state")
    ResponseEntity<Void> reset() {
        completionScheduler.reset();
        transactionStore.reset();
        overrideStore.reset();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/state/reset")
    ResponseEntity<Void> resetAlias() {
        return reset();
    }

    @GetMapping("/transactions/{provider}/{reference}")
    ResponseEntity<SimulatedTransaction> transaction(
            @PathVariable String provider,
            @PathVariable String reference
    ) {
        return transactionStore.find(new TransactionKey(provider, reference))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    record ProviderMetadata(
            String id,
            boolean enabled,
            String specificationVersion,
            String securityMode
    ) {
    }

    record ProfilesResponse(String active, java.util.Set<String> available) {
    }

    record OverrideRequest(
            @NotBlank String provider,
            @NotBlank String reference,
            @NotNull SimulationScenario scenario
    ) {
    }
}