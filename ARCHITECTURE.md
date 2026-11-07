# Simulator Architecture

## 1. Purpose

This application simulates many external payment providers from one codebase. The current provider integrations are:

- NIBSS NIP
- Interswitch Quickteller Service v5 (Send Money, Bills Payment and Airtime)
- Onafriq Bill Payment

A consuming application integrates with the simulator exactly as it would with the real provider. It is used for local development, automated integration tests, demonstrations, and controlled failure testing.

The architecture is a **modular monolith with provider adapters**. It keeps deployment simple while isolating each provider's protocol, configuration, behavior capabilities, and tests. Provider modules can later be extracted into separate services without redesigning their core contracts.

## 2. Architecture decision

### Contract fidelity rule

The provider-facing surface of each module must be indistinguishable from the provider's specification:

1. Behavior follows the provider's API specification: paths, methods, headers, authentication, request and response fields, response codes, HTTP statuses, and error bodies.
2. A client integrated against the simulator needs no change when it switches to the real provider. Only the base URL and credentials differ.
3. Several providers can be used at the same time from one simulator process.
4. Nothing simulator-specific reaches a provider client: no simulator headers, query parameters, body fields, response fields, response codes, HTTP statuses, or error shapes the specification does not define.

Simulation control therefore lives entirely in a separate control plane (the Admin API and configuration). A provider operation may only be driven into outcomes its specification can express; the control plane rejects anything else instead of inventing a response.

Where a specification is incomplete, the module documents the gap in its provider document and does not fill it with assumptions presented as provider behavior.

### Why a modular monolith

A single Spring Boot process is the best starting point because it provides:

- one application to build, configure, run, and observe;
- reusable infrastructure for delays, errors, state, logging, and administration;
- strong boundaries between providers without the operational cost of many services;
- fast in-process integration tests;
- a controlled path to independent deployment if one simulator eventually needs different scaling, security, or availability.

Starting with microservices would add service discovery, ports, deployment manifests, distributed tracing, and network failure modes before they provide useful value. A single unstructured package, on the other hand, would cause provider-specific details to leak into shared code. The modular monolith is the middle ground.

### Core design rule

Provider wire contracts belong to provider modules. Shared infrastructure must not contain NIBSS, Interswitch, or Onafriq request fields, response codes, signatures, or endpoint assumptions.

Each provider module owns:

- its HTTP endpoints and transport behavior;
- request and response DTOs;
- validation, authentication, signing, encryption, and serialization rules, including error responses;
- translation to and from internal simulation commands;
- provider-specific response codes and messages;
- the declaration of which outcomes and transaction statuses each operation can express;
- fixtures;
- contract and integration tests.

The shared core owns only provider-neutral simulation concepts: behavior selection, delays, connection-level timeouts, ephemeral transaction state, pending transitions, and telemetry.

### Storage-free operating principle

The simulator must not depend on a database, message broker, cache server, mounted volume, or other durable storage. It should start as a lightweight standalone process and be safe to discard and restart at any time.

When a flow needs short-lived state, such as transfer requery or a pending result, keep it only in bounded application memory. All such state is intentionally lost on restart and must have a configurable time-to-live and maximum entry count. Static provider fixtures and behavior profiles belong in version-controlled YAML or JSON resources loaded at startup; these are configuration, not runtime storage.

Prefer deterministic behavior derived from the request reference, request field values, and configured profiles and rules when state is not required. Do not introduce a persistent `TransactionStore` implementation unless this architecture decision is explicitly changed later.

## 3. Component model

```text
                          +---------------------------+
Client application -----> | Provider-specific API     |   exact specification surface;
(unchanged from prod)     | NIBSS / Interswitch /     |   optional per-provider base path
                          | Onafriq                   |
                          +-------------+-------------+
                                        |
                          validate, authenticate, translate
                                        |
                          +-------------v-------------+
                          | Provider adapter          |   spec field names as attributes;
                          | command/result mapping    |   outcome -> documented response
                          +-------------+-------------+
                                        |
                          +-------------v-------------+        +----------------+
                          | Simulation core           | <----- | Admin API      |
                          | behavior resolution,      |        | (control plane)|
                          | delay, timeout, state     |        +----------------+
                          +------+------+-------------+
                                 |
                          +------v------+
                          | Ephemeral   |
                          | memory      |
                          +-------------+
```

### Provider API layer

Each provider is mounted at its specification paths, for example:

```text
/NIPWS/NIPInterface, /NIPWS/NIPTSQInterface, /NIPWS/NIPNEInterface
                                    NIBSS NIP v9.5 SOAP web service with SSM encryption
/nip/v9.4/...                       NIBSS (local HTTP XML wrapper of the same messages)
/passport/oauth/token               Interswitch OAuth
/quicktellerservice/api/v5/...      Interswitch Quickteller Service v5
/services/...                       Onafriq Biller Aggregation Platform
```

A client sets the provider base URL to the simulator. If two providers ever publish the same path, give one of them `simulator.providers.<id>.base-path` (for example `/onafriq`) and include that prefix in the client's base URL; the prefix is a deployment setting, exactly like a different provider host. Spring MVC fails startup when two providers map an identical route.

Authentication, error handling, and availability are scoped by the provider's Java package, never by URL prefix, so they follow the provider when it is mounted under a base path.

A provider module may also own control-plane endpoints, for example key exchange or calls in which the simulator plays the provider towards the client (NIBSS inward). Such controllers are annotated `@ControlPlaneEndpoint` and mapped under `/admin/...`: the provider base path and the disabled-provider check never apply to them, so they always stay behind the admin token, and they render admin errors, never provider errors.

### Provider adapter

The adapter is an anti-corruption layer. It prevents one provider's terminology from becoming the application's shared model. Its flow is:

1. Decode and validate the provider request; render invalid requests in the provider's documented error format.
2. Verify provider-specific credentials or signatures.
3. Translate the request into a provider-neutral command: operation name, reference, amount, and request field values keyed by their specification names.
4. Ask the core to simulate the operation.
5. Translate the outcome or transaction status into the exact provider response contract.

New providers are added through module registration, not by editing existing provider implementations.

### Simulation core

```java
public interface SimulatedProvider {            // one per provider module, in the module root package
    String id();
    List<ProviderOperation> operations();       // outcomes and query statuses the spec can express
}

public interface ScenarioResolver {
    ResolvedBehavior resolve(ScenarioQuery query);
}

public interface TransactionStore {
    Optional<SimulatedTransaction> find(TransactionKey key);
    SimulatedTransaction save(SimulatedTransaction transaction);
    Optional<SimulatedTransaction> saveIfAbsent(SimulatedTransaction transaction);
}
```

- `ProviderOperationSimulator`: operations without transaction state, such as name or balance enquiry. Returns success or failure, or produces a timeout.
- `TransactionSimulationService`: operations that create a transaction visible to a later provider query. Records the transaction on receipt, detects duplicates atomically, schedules pending transitions, then holds and responds or times out.
- `ProviderRegistry`: validates that configured providers, operations, profiles, and rules match the registered modules.

### Behavior selection

Behavior is data-driven and selected only through the control plane. A **profile** is a named behavior in `simulator.profiles`:

| Field | Meaning |
|---|---|
| `outcome` | `success`, `failure` (provider-declared), `pending`, or `timeout` |
| `delay` | Time the request is held before the response, or before the connection is closed |
| `completes-as` | Final status of a pending or timed-out transaction: `success`, `failed`, `reversed`, or `never` |
| `transition-delay` | Time from receipt until the final status is reached |
| `received` | For `timeout`: whether the provider recorded the request |

A **timeout** is what a client sees when a provider never answers: the request is held for `delay` and the connection is then closed without a status line, headers, or body. If the profile says the provider received the request, the transaction is recorded on receipt and reaches `completes-as`, so the client's subsequent provider query observes a real provider state.

Precedence, from highest to lowest:

1. one-time override for a provider reference;
2. override for the next N requests of a provider operation (or of any operation of the provider);
3. test-data rule: a request field, named as in the specification, equals a configured value;
4. profile assigned to the provider operation;
5. profile assigned to the provider;
6. global default profile.

Provider-wide selections apply only to operations that can express them. Operation-specific selections are rejected when the operation's specification cannot express the outcome or the resulting query statuses.

### Stateful transaction simulation

```text
received -> PENDING -> SUCCESS | FAILED | REVERSED     (pending, timeout)
received -> SUCCESS | FAILED                           (success, failure)
not received                                           (timeout with received: false)
```

The transaction key is provider ID plus provider reference. Receiving the same reference again returns the provider-specific duplicate response. State is a bounded, TTL-limited, thread-safe in-memory map with explicit reset. Process restarts clear transactions, pending transitions, overrides, and runtime rules.

### Administration and control plane

```text
GET    /admin/providers                                   providers, operations, capabilities, assignments
GET    /admin/profiles
PUT    /admin/providers/{provider}/profile
DELETE /admin/providers/{provider}/profile
PUT    /admin/providers/{provider}/operations/{operation}/profile
DELETE /admin/providers/{provider}/operations/{operation}/profile
POST   /admin/overrides        GET /admin/overrides        DELETE /admin/overrides/{id}
POST   /admin/rules            GET /admin/rules            DELETE /admin/rules/{id}
GET    /admin/transactions/{provider}/{reference}
POST   /admin/state/reset      (alias: DELETE /admin/state)
GET    /admin/providers/nibss-nip/keys[/simulator-public]  PUT /admin/providers/nibss-nip/keys/client-public
POST   /admin/providers/nibss-nip/inward/{name-enquiry|credit}   (simulator plays NIBSS)
```

Reset clears runtime state and restores the configured profile assignments and rules. Administrative endpoints are never part of a simulated provider contract.

## 4. Project organization

```text
src/main/java/com/edgefort/simulator/
  SimulatorApplication.java
  config/                      typed simulator properties and core wiring
  core/
    application/               operation and transaction simulation, delays, transitions, telemetry
    provider/                  SimulatedProvider, ProviderOperation, ProviderRegistry
    scenario/                  profiles, assignments, overrides, rules, resolver
    state/                     transaction model and in-memory store
  admin/api/                   control plane
  infrastructure/web/          base paths, provider availability, timeouts, capacity
  provider/
    nibss/nip/                 NibssNipProvider + api/ application/ contract/ config/ security/ (SSM) soap/ control/ (admin)
    interswitch/               InterswitchProvider + common/ transfer/ billpayment/
    onafriq/billpayment/       OnafriqBillPaymentProvider + api/ application/ config/ contract/
```

Within a provider, `contract` contains wire DTOs and response codes, `api` contains transport entry points, error rendering, and authentication, and `application` performs mapping and orchestration. ArchUnit tests enforce that the core does not depend on providers, admin, or web infrastructure, and that providers do not depend on each other.

### Gradle modules when boundaries stabilize

Move to a multi-module build when multiple contributors are adding providers or accidental coupling becomes difficult to control:

```text
simulator/
  simulator-app/
  simulator-core/
  simulator-admin/
  simulator-test-support/
  providers/
    nibss-nip-simulator/
    interswitch-simulator/
    onafriq-bill-payment-simulator/
```

`simulator-app` is the composition root. Provider modules may use `simulator-test-support` only in test code. Do not place provider implementations in `simulator-core`.

## 5. Provider design

Each provider document records the reviewed specification, implemented routes, operation names, declared outcomes and query statuses, test-data match fields, response-code mapping, error rendering, and every known deviation:

- `NIBSS_NIP.md`
- `INTERSWITCH_SEND_MONEY_V5.md`
- `INTERSWITCH_BILLS_PAYMENT_V5.md`
- `ONAFRIQ_BILL_PAYMENT_V1.md`

### Specification handling

Provider specifications can be versioned, restricted, or changed. Record the simulated specification version in each module and expose it through provider metadata. Never invent field lengths, response codes, signing rules, or encryption behavior from assumptions. Store examples that come from restricted documents only where repository access and data-classification policy permit it.

Open specification dependencies:

- NIBSS: the SOAP response element (`return`) is unverified, pending NIBSS's WSDL or a captured NIBSS response.
- Onafriq: the HMAC signing specification is required to support HMAC-authenticated clients.
- Recorded sandbox responses, where available, are required for byte-level contract tests.

## 6. Configuration model

Use typed Spring configuration properties and keep secrets outside source control. Every provider's metadata, mounting, default behavior, and provider-specific settings live under `simulator.providers.<provider-id>`:

```yaml
simulator:
  default-profile: happy-path
  profiles:
    failure:
      outcome: failure
    timeout:
      outcome: timeout
      delay: 60s
      completes-as: success
  providers:
    interswitch:
      enabled: true
      specification-version: Quickteller Service API v5 public documentation (Send Money, Bills Payment and Airtime)
      security-mode: oauth-client-credentials-and-bearer
      base-path: ""
      profile: null
      operation-profiles:
        transfer: failure
      rules:
        - operation: name-enquiry
          field: accountId
          equals: "0000000000"
          profile: failure
      config:
        client-id: ${INTERSWITCH_CLIENT_ID:simulator-client}
```

Each provider module binds a dedicated validated properties class to `simulator.providers.<provider-id>.config`. Startup fails when a configured provider has no module (or the reverse), a profile delay is negative or above `simulator.limits.max-delay`, a base path is malformed, two providers map the same route, or a configured profile, operation, rule field, or assignment cannot be expressed by the provider specification.

## 7. Request flow

1. The provider controller receives its native request at the specification path.
2. The provider validates transport, security, and fields, and renders failures in its documented error format.
3. The adapter creates a provider-neutral command with specification-named attributes.
4. The resolver selects a behavior from overrides, rules, and profile assignments.
5. For transactional operations, the core checks duplicates, records the transaction on receipt, and schedules any pending transition.
6. The core holds the request for the configured delay.
7. On timeout, the connection is closed without a response; otherwise the adapter maps the outcome or status to the native response.
8. Metrics and logs record provider, operation, profile, selection source, and outcome, never request field values.

## 8. Testing strategy

Every provider module needs tests at several boundaries:

- **Contract tests:** field names, required fields, formats, response codes, headers, signing, and error formats. Recorded sandbox responses are the reference where available.
- **Behavior tests:** every declared outcome, pending completion, timeout as a dropped connection followed by a provider query, duplicates, and rejection of undeclared outcomes.
- **Control-plane tests:** precedence, operation capability checks, overrides, rules, and reset.
- **HTTP integration tests:** real-port tests for dropped connections; MockMvc elsewhere.
- **Architecture tests:** core independence and no cross-provider dependencies.
- **Consumer smoke tests:** run the consuming application against the provider sandbox and against the simulator with only the base URL changed.

Contract fixtures must not contain real account details, credentials, keys, certificates, or customer data.

## 9. Observability and security

Metrics `simulator.simulations` (tags `provider`, `operation`, `profile`, `source`, `outcome`), `simulator.duplicates`, and the gauge `simulator.held.requests` are exposed through `/actuator/metrics`. Logs record the same dimensions and never request field values.

Apply the following safeguards:

- never store or commit real provider credentials or private keys;
- restrict the admin API and state-reset operations;
- cap configured delays and concurrent held connections; capacity exhaustion returns a bare HTTP 503;
- enforce TTL and entry-count limits for ephemeral transactions, overrides, and rules;
- separate simulator test credentials from production provider secrets.

The simulator should not be publicly reachable by default. It is not a substitute for provider certification or end-to-end testing against an official sandbox.

## 10. Scaling and extraction path

Run all providers in one application instance. Because runtime state is intentionally local and ephemeral, do not load-balance stateful simulation flows across multiple instances. If multiple instances are needed for traffic volume, use sticky routing or restrict them to stateless operations; each instance remains independent and restartable.

Extract a provider into its own deployable service only when there is measurable need, such as:

- incompatible network or certificate requirements;
- significantly different traffic or resource usage;
- independent release ownership;
- provider-specific availability requirements;
- failure isolation that cannot be achieved in-process.

Because provider modules communicate with shared capabilities through narrow interfaces, extraction can replace in-process interfaces with an internal API or event contract. Do not introduce that network boundary or durable coordination in advance.

## 11. Provider onboarding checklist

For every additional provider:

1. Record the provider, product, protocol, and specification version in a provider document.
2. List only the operations required by consumers.
3. Create a provider package with a `SimulatedProvider` in its root and no dependencies on other providers.
4. Declare each operation's outcomes, query statuses, and match fields strictly from the specification.
5. Add native DTOs, validation, security, error rendering, and semantic mappings.
6. Define reference uniqueness, idempotency, and transaction lifecycle behavior.
7. Add a `simulator.providers.<id>` entry and, if needed, a typed `config` binder.
8. Add behavior, contract, and HTTP integration tests, including dropped-connection timeouts and base-path mounting.
9. Run consumer smoke tests before declaring the provider supported.

## 12. Status

| Area | State |
|---|---|
| Core behavior model, control plane, timeouts, base paths, metrics | Implemented |
| NIBSS NIP messages 010–023 | Implemented over the v9.5 SOAP web service with SSM (PGP) encryption, inward flow through the admin API, and the v9.4 local HTTP XML routes; SOAP response element unverified pending NIBSS's WSDL or a captured response |
| Interswitch Send Money, Bills Payment and Airtime v5 | Implemented from public documentation |
| Onafriq bill payment | Implemented from public documentation; HMAC authentication pending the signing specification |
| Recorded-response contract tests | Pending sandbox recordings |
| Gradle multi-module build | Deferred until boundaries or ownership require it |
