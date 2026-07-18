# Simulator Architecture

## 1. Purpose

This application is intended to simulate many external payment providers from one codebase. The first provider integrations are:

- NIBSS NIP
- Interswitch Transfer

The simulator should reproduce each provider's public contract closely enough that a consuming application can use the simulator in local development, automated integration tests, demonstrations, and controlled failure testing.

The recommended starting architecture is a **modular monolith with provider adapters**. It keeps deployment simple while isolating each provider's protocol, configuration, scenarios, and tests. Provider modules can later be extracted into separate services without redesigning their core contracts.

## 2. Architecture decision

### Why a modular monolith

A single Spring Boot process is the best starting point because it provides:

- one application to build, configure, run, and observe;
- reusable infrastructure for delays, errors, state, logging, and administration;
- strong boundaries between providers without the operational cost of many services;
- fast in-process integration tests;
- a controlled path to independent deployment if one simulator eventually needs different scaling, security, or availability.

Starting with microservices would add service discovery, ports, deployment manifests, distributed tracing, and network failure modes before they provide useful value. A single unstructured package, on the other hand, would cause provider-specific details to leak into shared code. The modular monolith is the middle ground.

### Core design rule

Provider wire contracts belong to provider modules. Shared infrastructure must not contain NIBSS or Interswitch request fields, response codes, signatures, or endpoint assumptions.

Each provider module owns:

- its HTTP endpoints and transport behavior;
- request and response DTOs;
- validation, authentication, signing, encryption, and serialization rules;
- translation to and from internal simulation commands;
- provider-specific response codes and messages;
- default scenarios and fixtures;
- contract and integration tests.

The shared core owns only provider-neutral simulation concepts such as scenario selection, delays, deterministic outcomes, ephemeral transaction state, audit events, and clock/id abstractions.

### Storage-free operating principle

The simulator must not depend on a database, message broker, cache server, mounted volume, or other durable storage. It should start as a lightweight standalone process and be safe to discard and restart at any time.

When a flow needs short-lived state, such as transfer requery or a pending result, keep it only in bounded application memory. All such state is intentionally lost on restart and must have a configurable time-to-live and maximum entry count. Static provider fixtures and scenario profiles belong in version-controlled YAML or JSON resources loaded at startup; these are configuration, not runtime storage.

Prefer deterministic behavior derived from the request reference, configured profile, and seed when state is not required. This allows the same request to produce the same outcome without persisting transaction history. Do not introduce a persistent `TransactionStore` implementation unless this architecture decision is explicitly changed later.

## 3. Recommended component model

```text
                         +---------------------------+
Client application ---->| Provider-specific API     |
                         | NIBSS / Interswitch       |
                         +-------------+-------------+
                                       |
                         validate and translate
                                       |
                         +-------------v-------------+
                         | Provider adapter          |
                         | command/result mapping    |
                         +-------------+-------------+
                                       |
                         +-------------v-------------+
                         | Simulation core           |
                         | scenarios, behavior,      |
                         | delay, state, audit       |
                         +------+------+-------------+
                                |      |
                       +--------v--+ +-v-------------+
                       | Ephemeral | | Admin API     |
                       | memory    | | profiles/rules|
                       +-----------+ +---------------+
```

### Provider API layer

Expose separate provider namespaces rather than a generic endpoint with a `provider` parameter. For example, development URLs may look like:

```text
/nip/v9.4/...
/quicktellerservice/api/v5/...
```

These paths are illustrative. Exact paths, headers, media types, and status behavior must be copied from the version of each provider specification used by the consuming system. If the consuming system requires the provider's exact root path, use host-based routing or configurable base paths at deployment time instead of changing the internal design.

### Provider adapter

The adapter is an anti-corruption layer. It prevents one provider's terminology from becoming the application's shared model. Its flow is:

1. Decode and validate the provider request.
2. Verify provider-specific credentials or signatures when enabled.
3. Translate the request into a small provider-neutral simulation command.
4. Ask the simulation engine for the configured behavior.
5. Apply transaction state changes where appropriate.
6. Translate the result into the exact provider response contract.
7. Apply provider-specific signing or encryption when enabled.

Do not create one large `ProviderService` containing `if/else` or `switch` branches for every provider. New providers should be added through module registration, not by editing existing provider implementations.

### Simulation core

The shared core should define narrow contracts similar to the following concepts:

```java
public interface SimulationHandler<C extends SimulationCommand, R extends SimulationResult> {
    ProviderOperation operation();
    R handle(C command, SimulationContext context);
}

public interface ScenarioResolver {
    SimulationBehavior resolve(ScenarioQuery query);
}

public interface TransactionStore {
    Optional<SimulatedTransaction> find(TransactionKey key);
    SimulatedTransaction save(SimulatedTransaction transaction);
}
```

These are proposed boundaries, not code that must be copied unchanged. Keep commands focused on information the core actually needs, such as operation, amount, account reference, client reference, and correlation metadata. Preserve the complete provider DTO only in the provider module or an audit-safe representation.

Important shared concepts are:

- `ProviderId`: stable identifier such as `nibss-nip` or `interswitch-transfer`;
- `ProviderOperation`: module-defined operation key;
- `SimulationContext`: profile, correlation id, clock, and request metadata;
- `SimulationBehavior`: success/failure result, delay, timeout, malformed response, or connection behavior;
- `ScenarioResolver`: selects behavior using deterministic rules;
- `TransactionStore`: stores simulated transaction lifecycle data;
- `AuditPublisher`: records scenario selection and outcome without leaking secrets.

### Scenario engine

Scenarios must be data-driven so that common behaviors do not require custom controller code. A behavior should be selectable by:

- provider and operation;
- active simulation profile, such as `happy-path`, `timeout`, or `unstable`;
- request matchers, such as amount, account suffix, reference, or an explicitly allowed test header;
- a one-time override associated with a correlation or client reference;
- probability, with a configured seed when deterministic test execution is required.

Recommended precedence, from highest to lowest, is:

1. one-time request/correlation override;
2. explicit request matcher in the active profile;
3. operation rule in the active profile;
4. provider default;
5. global success default.

Typical behavior types are:

- immediate success;
- provider-declared failure;
- delayed success or failure;
- no response/timeout;
- HTTP or transport error;
- malformed body for resilience testing;
- duplicate response;
- pending response followed by success, failure, or reversal;
- intermittent behavior with deterministic randomness.

Delay and timeout are different behaviors and must remain explicit:

- **delayed response:** hold the request for a configured duration, then return success or provider-declared failure;
- **client-visible timeout:** hold the connection longer than the consumer's configured timeout, with a simulator-side safety cap so resources are eventually released;
- **immediate transport failure:** close the connection or return the configured HTTP error without a provider response body;
- **event/callback timeout:** accept the request, optionally return `PENDING`, and deliberately suppress the expected callback or completion event;
- **late event:** emit an in-process scheduled callback after the consumer's expected event deadline to test late-arrival handling.

Scheduled delays and events must use bounded executors rather than one unmanaged thread per request. Configuration must cap delay duration, queued tasks, and concurrent held requests. Restarting the simulator cancels all pending delays and events by design.

Keep the scenario selection independent of provider response construction. The core can select a semantic outcome such as `INVALID_DESTINATION_ACCOUNT`; each adapter maps that outcome to its own response code and payload.

### Stateful transaction simulation

Transfer flows need state because a status/requery operation must agree with an earlier transfer operation. A minimal lifecycle can be:

```text
RECEIVED -> PROCESSING -> SUCCESS
                       -> FAILED
SUCCESS  -> REVERSED
```

Use a transaction key that includes the provider and provider/client reference to prevent collisions. Define idempotency explicitly: receiving the same transfer reference should either return the original result or the provider-specific duplicate response, according to the selected scenario and real contract.

Use only an in-memory implementation. It should be a bounded, thread-safe map with configurable expiry and explicit reset support. Process restarts intentionally clear transaction state, pending transitions, one-time overrides, and scheduled callbacks. Requery consistency is guaranteed only while the same simulator process is running and before the entry expires.

For completely stateless scenarios, derive status from the provider/client reference and deterministic seed instead of adding an entry to the map. Keep the `TransactionStore` interface as an internal abstraction for testability, but do not provide database, file, Redis, or other durable implementations.

### Administration and control plane

Keep simulation administration separate from provider-facing endpoints. A protected internal API can provide:

```text
GET    /admin/providers
GET    /admin/profiles
PUT    /admin/profiles/{profile}/activate
POST   /admin/overrides
DELETE /admin/overrides/{id}
POST   /admin/state/reset
GET    /admin/transactions/{provider}/{reference}
```

An override could express: "for the next NIBSS NIP transfer with reference X, wait 30 seconds and then return the configured timeout response." Administrative endpoints must never accidentally become part of a simulated provider contract.

## 4. Project organization

### Stage 1: package-by-feature in the current project

The repository is currently a minimal single-module Spring Boot application. Begin with strict feature packages to establish boundaries without first restructuring the Gradle build:

```text
src/main/java/com/edgefort/providersimulator/
  ProviderSimulatorApplication.java
  core/
    model/
    scenario/
    state/
    audit/
    time/
  admin/
    api/
    application/
  provider/
    nibss/nip/
      api/
      application/
      config/
      contract/
      scenario/
    interswitch/transfer/
      api/
      application/
      config/
      contract/
      scenario/
  infrastructure/
    persistence/
    observability/
    web/
```

Within a provider, `contract` contains wire DTOs and response codes, `api` contains transport entry points, `application` performs mapping and orchestration, and `scenario` contains provider defaults/mappings. Avoid imports from one provider package into another.

Use package-boundary tests, for example with ArchUnit, when implementation begins. They should enforce that provider modules can depend on `core`, but cannot depend on each other, and that `core` cannot depend on provider packages.

### Stage 2: Gradle modules when boundaries stabilize

Move to a multi-module build when multiple contributors are adding providers or accidental coupling becomes difficult to control:

```text
simulator/
  simulator-app/
  simulator-core/
  simulator-admin/
  simulator-test-support/
  providers/
    nibss-nip-simulator/
    interswitch-transfer-simulator/
```

Dependency direction must remain:

```text
simulator-app
  -> simulator-admin
  -> nibss-nip-simulator       -> simulator-core
  -> interswitch-transfer-simulator -> simulator-core
```

`simulator-app` is the composition root and should contain little beyond application startup and module wiring. Provider modules may use `simulator-test-support` only in test code. Do not place provider implementations in `simulator-core`.

### Optional module discovery

Explicit Spring configuration imports are preferable while the number of providers is small because startup behavior is easy to understand. If the provider count becomes large, each module can publish a Spring Boot auto-configuration and a `ProviderModuleDescriptor`. Discovery should be used only for registration and metadata; it should not replace typed application contracts with reflection or arbitrary maps.

## 5. Starting provider design

### NIBSS NIP module

Implement only the NIP operations required by the consuming application, then expand. Likely starting capabilities include name enquiry, funds transfer, and transaction status/requery, but operation names and payload details must be verified against the applicable NIBSS specification and certification environment.

The module should own:

- exact NIP request/response models and field formatting;
- institution/channel identifiers used by the simulator;
- request validation and required-field behavior;
- response-code mapping for semantic simulation outcomes;
- session/reference generation and duplicate handling;
- cryptographic or transport behavior required by the applicable specification;
- name-enquiry fixtures keyed by account and institution;
- state transitions shared by transfer and status operations.

Initial NIBSS scenarios should cover successful and unsuccessful name enquiry, successful transfer, invalid destination, insufficient funds response where relevant to the simulated contract, duplicate reference/session, pending transfer, timeout, status after each terminal state, and reversal behavior if consumed by the client.

### Interswitch Transfer module

Implement the transfer operations actually called by the consuming application, typically account/name validation, transfer initiation, and status/requery. Verify endpoint versions, headers, authentication, payloads, and error contracts against the applicable Interswitch documentation before coding.

The module should own:

- exact Interswitch request/response models;
- client credential, token, signature, or certificate simulation required by the chosen API version;
- response-code and HTTP-status mapping;
- transfer reference generation and duplicate rules;
- beneficiary fixtures;
- transfer and requery state transitions;
- provider-specific timeout and malformed-response behavior.

Initial Interswitch scenarios should mirror the NIBSS semantic coverage while returning Interswitch-specific wire responses. Sharing semantic outcomes does not mean sharing provider DTOs or response codes.

### Specification handling

Provider specifications can be versioned, restricted, or changed. Record the simulated specification version in each module and expose it through provider metadata. Never invent field lengths, response codes, signing rules, or encryption behavior from assumptions. Store examples that come from restricted documents only where repository access and data-classification policy permit it.

## 6. Configuration model

Use typed Spring configuration properties and keep secrets outside source control. A possible shape is:

```yaml
simulator:
  default-profile: happy-path
  deterministic-seed: 1001
  providers:
    nibss-nip:
      enabled: true
      base-path: /nip/v9.4
      specification-version: "NIP v9.4"
      security-mode: not-configured-wsdl-security-not-supplied
    interswitch-transfer:
      enabled: true
      base-path: /simulators/interswitch/transfer
      specification-version: "configured-version"
      security-mode: relaxed
```

Recommended security modes are:

- `relaxed`: validate business payloads but make local development easy;
- `strict`: enforce credentials, signatures, certificates, timestamps, and replay protection as the real provider does.

Mode differences must be explicit. Do not silently accept invalid security data in strict mode.

Profiles and scenario rules should be YAML resources. Runtime profile selection and overrides remain in memory and disappear on restart; no database-backed editing is required. Configuration changes should be validated at startup so invalid response codes, unknown operations, negative delays, delays above the safety cap, and contradictory matchers fail early.

## 7. Request flow example

A transfer request should follow this sequence:

1. The NIBSS or Interswitch controller receives its native request.
2. A provider-specific validator validates transport, security, and fields.
3. The adapter creates a canonical `TransferCommand` while retaining provider metadata required for the response.
4. The scenario resolver selects a behavior using provider, operation, active profile, and request matchers.
5. The transaction service checks idempotency and current state.
6. The behavior executor applies a deterministic delay/error/result.
7. Any short-lived transaction state is saved to bounded memory, or the outcome is derived deterministically without state.
8. The provider adapter maps the semantic result to the native response code and body.
9. The response is signed or encrypted when strict mode requires it.
10. Structured audit and metrics are emitted with secrets and sensitive account data masked.

## 8. Testing strategy

Every provider module needs tests at several boundaries:

- **Contract tests:** JSON/XML field names, required fields, formats, response codes, headers, signing, and encryption.
- **Adapter tests:** native request to canonical command and semantic result to native response.
- **Scenario tests:** precedence, matching, deterministic randomness, delays, malformed responses, and one-time overrides.
- **State tests:** idempotency, duplicate references, pending-to-terminal transitions, status consistency, and reversal.
- **HTTP integration tests:** application endpoint behavior using the consuming application's real sample requests where permitted.
- **Architecture tests:** core independence and no cross-provider dependencies.
- **Consumer smoke tests:** point the consuming application at this simulator and execute its critical transfer flows.

Maintain sanitized golden request/response fixtures per provider and specification version. Contract fixtures should not contain real account details, credentials, keys, certificates, or customer data.

Use a controllable clock and delay abstraction. Tests must not wait for real 30-second timeouts; production simulation may delay or hold a connection, while unit tests advance a fake clock or verify the selected delay behavior.

## 9. Observability and security

Emit structured logs and metrics tagged with `provider`, `operation`, `profile`, `scenario`, and `outcome`. Useful metrics include request count, response latency, active delayed requests, semantic failures, transport failures, and scenario selection count.

Apply the following safeguards:

- mask account numbers and any personally identifiable information in logs;
- never store or commit real provider credentials or private keys;
- restrict the admin API and state-reset operations;
- cap configured delays and concurrent held connections to prevent resource exhaustion;
- cap request body sizes and validate content types;
- separate strict security test material from production provider secrets;
- enforce TTL and entry-count limits for ephemeral transaction, override, and audit state;
- clearly identify responses as simulator traffic in internal observability, without changing provider contracts.

The simulator should not be publicly reachable by default. A strict simulator is useful for integration confidence, but it is not a substitute for provider certification or end-to-end testing against an official sandbox.

## 10. Scaling and extraction path

Run all providers in one application instance. Because runtime state is intentionally local and ephemeral, do not load-balance stateful simulation flows across multiple instances. If multiple instances are needed for traffic volume, use sticky routing or restrict them to deterministic stateless scenarios; each instance remains independent and restartable.

Extract a provider into its own deployable service only when there is measurable need, such as:

- incompatible network or certificate requirements;
- significantly different traffic or resource usage;
- independent release ownership;
- provider-specific availability requirements;
- failure isolation that cannot be achieved in-process.

Because provider modules communicate with shared capabilities through narrow interfaces, extraction can replace in-process interfaces with an internal API or event contract. Do not introduce that network boundary or durable coordination in advance.

## 11. Proposed implementation plan

### Phase 1: foundation

1. Create the `core`, `admin`, `provider`, and `infrastructure` package boundaries.
2. Define provider-neutral commands/results, `SimulationBehavior`, `ScenarioResolver`, `TransactionStore`, clock, delay, and audit interfaces.
3. Implement YAML-backed profiles, deterministic scenario resolution, a bounded TTL in-memory transaction store, and startup validation.
4. Add protected admin endpoints for metadata, profile selection, overrides, transaction lookup, and reset.
5. Add architecture and scenario-engine tests.

### Phase 2: NIBSS NIP vertical slice

1. Obtain and record the applicable NIP specification/version and sanitized examples.
2. Implement one complete operation, preferably name enquiry, from HTTP contract through scenario response.
3. Add transfer initiation with idempotent state handling.
4. Add transaction status/requery using saved state.
5. Implement strict security behavior required by the chosen specification.
6. Validate the consuming application's success, failure, pending, duplicate, and timeout flows.

### Phase 3: Interswitch Transfer vertical slice

1. Obtain and record the applicable API specification/version and sanitized examples.
2. Reuse core scenario and state contracts, but create independent Interswitch wire models and mappings.
3. Implement account validation/name enquiry, transfer, and status operations required by the consumer.
4. Implement strict authentication/signature behavior for the selected API version.
5. Run the same semantic scenario matrix and consumer smoke tests with Interswitch-specific assertions.

### Phase 4: hardening and provider onboarding

1. Preserve the storage-free design: document restart semantics and use deterministic stateless outcomes where possible.
2. Add metrics dashboards, in-memory TTL/size limits, delay/event concurrency limits, and audit controls.
3. Extract Gradle modules when package boundaries or team ownership justify it.
4. Create a provider onboarding checklist and module template from the two proven implementations.

## 12. Provider onboarding checklist

For every additional provider:

1. Record the provider, product, protocol, and specification version.
2. List only the operations required by consumers.
3. Create a provider package/module with no dependencies on other providers.
4. Add native DTOs, validation, security, and semantic mappings.
5. Define fixtures and the default scenario matrix.
6. Define reference uniqueness, idempotency, and transaction lifecycle behavior.
7. Add contract, adapter, scenario, state, and HTTP integration tests.
8. Add typed configuration and masked observability.
9. Verify strict and relaxed security modes.
10. Run consumer smoke tests before declaring the provider supported.

## 13. Definition of done for the first release

The first useful release should provide:

- NIBSS NIP and Interswitch Transfer as isolated provider features;
- operations required by the first consuming application, based on verified specifications;
- happy-path, provider-failure, pending, duplicate, delayed, timeout, and requery scenarios;
- provider event/callback suppression and late-event scenarios;
- deterministic profile/rule selection;
- bounded in-memory stateful and idempotent transfer behavior, with documented reset-on-restart semantics;
- no database, broker, external cache, mounted volume, or other runtime storage dependency;
- protected runtime controls and state reset;
- strict and relaxed security modes where applicable;
- sanitized contract fixtures and automated tests;
- masked structured logs and basic metrics;
- documentation identifying every simulated specification version and known deviation.

This design allows many simulators to coexist in one application without turning the codebase into a shared collection of provider-specific conditionals. It also avoids premature distributed-system complexity while preserving a practical path to independent services later.