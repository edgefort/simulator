# Simulator

This project runs lightweight simulations of multiple payment providers in one Spring Boot application.

A client integrates with the simulator exactly as it would with the real provider: same paths, headers, authentication, bodies, response codes, HTTP statuses, and error formats. Switching to the real provider changes only the base URL and credentials. Behavior such as failures, pending transactions, and timeouts is chosen out of band through the Admin API, never through provider requests.

The application owns behavior selection, bounded in-memory transaction state, delays, timeouts, and pending transitions. No database, broker, cache, or embedded mock server is required.

## Available providers

| Provider | Admin provider ID | Implemented interface | Payload and security |
|---|---|---|---|
| NIBSS NIP | `nibss-nip` | NIP v9.4 XML messages 010 through 023 | XML over local HTTP; the institution WSDL (SOAP binding) and message security are pending |
| Interswitch | `interswitch` | Quickteller Service v5: OAuth, Send Money (name enquiry, banks, transfer), Bills Payment and Airtime (discovery, customer validation, payment), shared transaction query | JSON; OAuth client credentials, bearer authorization, `TerminalId`, secure-data `SHA-512` MAC |
| Onafriq | `onafriq-bill-payment` | Biller Aggregation Platform API v1.0.0 bill-payment routes | JSON; `x-api-key` or `Authorization: Api-key`; HMAC authentication pending |

Each provider document lists the reviewed sources, routes, operation names, response-code mapping, error formats, and every known deviation:

- `NIBSS_NIP_V9_4.md`
- `INTERSWITCH_SEND_MONEY_V5.md`
- `INTERSWITCH_BILLS_PAYMENT_V5.md`
- `ONAFRIQ_BILL_PAYMENT_V1.md`

This is not a claim of provider certification. Before integrating a real consumer, confirm each provider module against the specification and credentials assigned to that consumer. Open specification dependencies:

- NIBSS: the institution WSDL and message-security specification, to replace the local HTTP XML transport with the production SOAP binding.
- Onafriq: the HMAC signing specification.
- Recorded sandbox responses, where available, for byte-level contract tests.

## Requirements and startup

- Java 25
- the included Gradle wrapper

Run the application:

```shell
./gradlew bootRun
```

`bootRun` listens on port `50001`; the Docker image listens on `8080`. Override either with `SERVER_PORT`, for example:

```shell
SERVER_PORT=9090 ./gradlew bootRun
```

The examples below use the container port `8080`.

### Docker

The repository includes a multi-stage `Dockerfile`. It builds the application with Java 25, copies only the executable JAR into the runtime image, runs as the unprivileged `simulator` user, and defines an Actuator health check. The JAR is built once on the native build platform and copied into the runtime image of each target platform.

Build and tag the image from the project root:

```shell
docker build --tag simulator:local .
```

`simulator:local` is a local tag, not the name of a published registry image. Replace it with your organization registry and tag when publishing or deploying the image.

Run the service in the foreground on its default container port and publish it on host port `8080`:

```shell
docker run --rm --name simulator \
  --publish 8080:8080 \
  --env SIMULATOR_ADMIN_TOKEN=change-me \
  simulator:local
```

Add `--detach` to run it in the background. You can then inspect health, follow logs, and stop the container with:

```shell
docker inspect --format '{{.State.Health.Status}}' simulator
docker logs --follow simulator
docker stop simulator
```

To configure a different service port inside the container, set `SERVER_PORT` and publish that container port. The host port may be the same or different; this example uses `9090` for both:

```shell
docker run --rm --name simulator \
  --publish 9090:9090 \
  --env SERVER_PORT=9090 \
  --env SIMULATOR_ADMIN_TOKEN=change-me \
  simulator:local
```

After default-port startup, open [Swagger UI](http://localhost:8080/swagger-ui.html) or check [Actuator health](http://localhost:8080/actuator/health). The container health check automatically follows the configured `SERVER_PORT`.

## Configuration

Provider configuration is grouped by provider ID under `simulator.providers.<provider-id>` in `application.yaml`:

```yaml
simulator:
  providers:
    interswitch:
      enabled: true
      specification-version: Quickteller Service API v5 public documentation (Send Money, Bills Payment and Airtime)
      security-mode: oauth-client-credentials-and-bearer
      base-path: ${INTERSWITCH_BASE_PATH:}
      config:
        client-id: ${INTERSWITCH_CLIENT_ID:simulator-client}
        client-secret: ${INTERSWITCH_CLIENT_SECRET:simulator-secret}
        access-token: ${INTERSWITCH_ACCESS_TOKEN:simulator-access-token}
        token-expires-in: ${INTERSWITCH_TOKEN_EXPIRES_IN:86400}
        terminal-id: ${INTERSWITCH_TERMINAL_ID:3PBL0001}
```

| Setting | Purpose |
|---|---|
| `enabled` | `false` hides every route of the provider (HTTP `404`, no body) |
| `base-path` | Optional prefix before the provider's specification paths, for example `/isw`. Use it only if two providers publish the same path; the client includes it in its base URL |
| `profile` | Behavior applied to every operation of the provider that can express it |
| `operation-profiles` | Behavior per operation, keyed by operation name |
| `rules` | Test-data rules: when a request field equals a value, apply a profile |
| `config` | Provider-specific settings, bound to a typed properties class |

Startup fails when configuration names an unknown provider, operation, field, or profile, when a profile cannot be expressed by the provider specification, when a delay exceeds `simulator.limits.max-delay`, or when two providers map the same route.

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `50001` (`8080` in the image) | HTTP port |
| `SIMULATOR_ADMIN_TOKEN` | `simulator` | Token required in `X-Admin-Token` for administration endpoints |
| `NIBSS_NIP_BASE_PATH` | empty | Optional NIBSS base path |
| `INTERSWITCH_BASE_PATH` | empty | Optional Interswitch base path |
| `ONAFRIQ_BASE_PATH` | empty | Optional Onafriq base path |
| `INTERSWITCH_CLIENT_ID` | `simulator-client` | OAuth client ID accepted by the token endpoint |
| `INTERSWITCH_CLIENT_SECRET` | `simulator-secret` | OAuth client secret accepted by the token endpoint |
| `INTERSWITCH_ACCESS_TOKEN` | `simulator-access-token` | Bearer token returned and accepted by the Interswitch API |
| `INTERSWITCH_TOKEN_EXPIRES_IN` | `86400` | Token lifetime in seconds |
| `INTERSWITCH_TERMINAL_ID` | `3PBL0001` | The only `TerminalId` the Interswitch API accepts |
| `ONAFRIQ_API_KEY` | `simulator-api-key` | API key accepted by Onafriq operations |
| `JAVA_TOOL_OPTIONS` | JVM default | Optional JVM options such as memory limits |

For example, place non-public local values in `~/.config/simulator/simulator.env`, outside the repository and image build context:

```dotenv
SIMULATOR_ADMIN_TOKEN=change-me
INTERSWITCH_CLIENT_ID=test-client
INTERSWITCH_CLIENT_SECRET=test-secret
INTERSWITCH_ACCESS_TOKEN=test-access-token
INTERSWITCH_TERMINAL_ID=3PBL0001
ONAFRIQ_API_KEY=test-onafriq-api-key
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
```

Then start the image with `docker run --rm --publish 8080:8080 --env-file ~/.config/simulator/simulator.env simulator:local`. Keep the file out of source control.

Do not use the local credential defaults outside isolated development. Docker Compose remains unnecessary because this storage-free simulator has no database, broker, cache, or other required service.

### Published image and versioning

Merging a pull request into the protected `main` branch runs the `Publish Docker image` GitHub Actions workflow. The workflow runs the tests, builds the repository `Dockerfile`, and publishes the image to GitHub Container Registry:

```text
ghcr.io/edgefort/simulator
```

Published images are multi-architecture. Docker pulls the variant matching the host automatically:

| Platform | Typical hosts |
|---|---|
| `linux/amd64` | Intel/AMD servers, cloud VMs, most CI runners |
| `linux/arm64` | Apple Silicon Macs, AWS Graviton, Azure Cobalt/Ampere, GCP Axion/Tau T2A |
| `linux/ppc64le` | IBM Power |
| `linux/s390x` | IBM Z and LinuxONE |

To build a multi-architecture image locally, use a Buildx builder with the `docker-container` driver:

```shell
docker buildx create --name multiarch --driver docker-container --use
docker buildx build --platform linux/amd64,linux/arm64,linux/ppc64le,linux/s390x --tag simulator:local .
```

The workflow uses the [Git Semantic Version](https://github.com/marketplace/actions/git-semantic-version) action to calculate versions directly from Git history, so no repository-maintained version script is required. The first successful publication is version `0.1.0`. Later versions are calculated from Conventional Commit messages merged since the previous `vX.Y.Z` Git tag:

- `feat:` or `feat(scope):` increments the minor version;
- a type followed by `!`, or a `BREAKING CHANGE:` footer, increments the major version;
- all other changes increment the patch version.

Use Conventional Commit formatting in pull request titles and commit messages. Protect `main` against direct pushes so every publication corresponds to a reviewed and merged pull request. A successful release creates the matching repository tag and publishes the immutable full version plus moving major, minor, and latest tags. For example, version `1.4.2` produces `1.4.2`, `1.4`, `1`, and `latest`.

Pull and run a published version with:

```shell
docker pull ghcr.io/edgefort/simulator:0.1.0
docker run --rm --name simulator \
  --publish 8080:8080 \
  --env SIMULATOR_ADMIN_TOKEN=change-me \
  ghcr.io/edgefort/simulator:0.1.0
```

Public packages can be pulled anonymously. If the package is private, authenticate with a GitHub personal access token that has `read:packages` permission:

```shell
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io --username YOUR_GITHUB_USERNAME --password-stdin
```

The workflow uses the repository `GITHUB_TOKEN`; no registry password secret is required. Repository Actions settings must allow workflow read/write access so the workflow can push the package and create release tags.

### Pull request validation

Every pull request targeting `main` runs the `Pull Request Validation` workflow. It executes the complete Gradle `check` lifecycle, including all automated tests, and builds the repository `Dockerfile` without publishing the image. A newer commit on the same pull request cancels the obsolete in-progress run.

Protect `main` in the GitHub repository settings and require the `Pull Request Validation / Test and build` status check before merging. The pull request workflow has read-only repository access and does not receive package or tag write permissions.

## Swagger and OpenAPI

Open [Swagger UI](http://localhost:8080/swagger-ui.html) after startup to inspect and invoke provider and administration operations. The generated OpenAPI document is available at [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

Swagger UI enables **Try it out** by default. To call an administration operation, select **Authorize** and enter the configured admin token in `AdminToken`. The default local admin token is `simulator`.

For Interswitch, first invoke `POST /passport/oauth/token` with HTTP Basic credentials and `grant_type=client_credentials`. Then select **Authorize**, enter the returned token in `InterswitchBearerToken`, and invoke the v5 operation. Enter only the token in Swagger; Swagger adds the `Bearer` prefix.

For Onafriq, select **Authorize** and enter the configured API key in `OnafriqApiKey`. Swagger sends it as `x-api-key`. The default local key is `simulator-api-key`; set `ONAFRIQ_API_KEY` to replace it.

Run all tests:

```shell
./gradlew test
```

## Using several providers at once

Point each client integration at the simulator as that provider's base URL. All providers are served by the same process at their specification paths:

| Provider | Paths |
|---|---|
| NIBSS NIP | `/nip/v9.4/...` |
| Interswitch | `/passport/oauth/token`, `/quicktellerservice/api/v5/...` |
| Onafriq | `/services/...` |

Transactions are keyed by provider and reference, so equal references used with different providers never collide. Behavior is selected per provider and per operation, so one provider can fail while another succeeds.

## Provider examples

NIBSS direct-credit transfer:

```shell
curl --request POST http://localhost:8080/nip/v9.4/fundtransfersingleitem_dc \
  --header 'Content-Type: application/xml' \
  --data '<FTSingleCreditRequest>
    <SessionID>000001100913103301000000000002</SessionID>
    <NameEnquiryRef>000001100913103301000000000001</NameEnquiryRef>
    <DestinationInstitutionCode>000002</DestinationInstitutionCode>
    <ChannelCode>1</ChannelCode>
    <BeneficiaryAccountName>Ajibade Oluwasegun</BeneficiaryAccountName>
    <BeneficiaryAccountNumber>2222002345</BeneficiaryAccountNumber>
    <BeneficiaryBankVerificationNumber>1033000442</BeneficiaryBankVerificationNumber>
    <BeneficiaryKYCLevel>1</BeneficiaryKYCLevel>
    <OriginatorAccountName>Adewale Hassan</OriginatorAccountName>
    <OriginatorAccountNumber>3333002345</OriginatorAccountNumber>
    <OriginatorBankVerificationNumber>1033000441</OriginatorBankVerificationNumber>
    <OriginatorKYCLevel>1</OriginatorKYCLevel>
    <TransactionLocation>6.4300747,3.4110715</TransactionLocation>
    <Narration>Simulation transfer</Narration>
    <PaymentReference>payment-reference-100</PaymentReference>
    <Amount>1250.50</Amount>
  </FTSingleCreditRequest>'
```

Interswitch access token, then transfer. The `mac` is the lowercase `SHA-512` digest of `100000566CA100000566ACNG` (secure-data version 12 field order):

```shell
curl --request POST 'http://localhost:8080/passport/oauth/token' \
  --user 'simulator-client:simulator-secret' \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data 'grant_type=client_credentials'

curl --request POST http://localhost:8080/quicktellerservice/api/v5/transactions/TransferFunds \
  --header 'Authorization: Bearer simulator-access-token' \
  --header 'TerminalId: 3PBL0001' \
  --header 'Content-Type: application/json' \
  --data '{
    "transferCode": "isw-reference-100",
    "mac": "9f4e4f53c57be63e1f08d8f07a7bc1a9461e4a7d5304043daa1ef54bd727b6cde148f4fbfc5e2ad8c4a60f78dfa76304de671fbeb70657b1628f14b6b6baa5e1",
    "termination": {
      "amount": "100000",
      "accountReceivable": {"accountNumber": "3001155245", "accountType": "00"},
      "entityCode": "044",
      "currencyCode": "566",
      "paymentMethodCode": "AC",
      "countryCode": "NG"
    },
    "sender": {"phone": "08124888436", "email": "jane@example.com", "lastname": "Doe", "othernames": "Jane"},
    "initiatingEntityCode": "PBL",
    "initiation": {"amount": "100000", "currencyCode": "566", "paymentMethodCode": "CA", "channel": "7"},
    "beneficiary": {"lastname": "Smith", "othernames": "John"}
  }'
```

Interswitch MTN airtime recharge (amounts in kobo):

```shell
curl --request POST http://localhost:8080/quicktellerservice/api/v5/Transactions \
  --header 'Authorization: Bearer simulator-access-token' \
  --header 'TerminalId: 3PBL0001' \
  --header 'Content-Type: application/json' \
  --data '{
    "paymentCode": "628051043",
    "customerId": "2348056731576",
    "customerMobile": "2348056731576",
    "amount": "50000",
    "requestReference": "airtime-local-001"
  }'
```

Onafriq airtime purchase and requery:

```shell
curl --request POST http://localhost:8080/services/airtime/request \
  --header 'x-api-key: simulator-api-key' \
  --header 'Content-Type: application/json' \
  --data '{
    "service_type": "mtn",
    "plan": "prepaid",
    "amount": 100,
    "phone": "07034953306",
    "agentId": "205",
    "agentReference": "onafriq-airtime-100"
  }'

curl --get http://localhost:8080/services/superagent/transaction/requery \
  --header 'x-api-key: simulator-api-key' \
  --data-urlencode 'agentReference=onafriq-airtime-100'
```

## Selecting provider behavior

Provider requests never carry simulator parameters. Behavior is a named **profile** selected through the Admin API or configuration.

| Profile | Behavior |
|---|---|
| `happy-path` | Success (the default) |
| `failure` | Provider-declared failure |
| `delayed-success` | Success after a 3-second hold |
| `pending` | Pending response; the transaction completes as success after 5 seconds |
| `pending-then-failed` | Pending response; completes as failed |
| `pending-then-reversed` | Pending response; completes as reversed (only where the provider can report reversals) |
| `pending-forever` | Pending response; never completes |
| `timeout` | No response: the connection is held for 60 seconds and closed without any bytes; the provider recorded the request and later queries report success |
| `timeout-then-failed` | No response; later queries report failure |
| `timeout-not-received` | No response; the provider has no record, so queries report not found |

Profiles are defined under `simulator.profiles` with `outcome` (`success`, `failure`, `pending`, `timeout`), `delay`, `completes-as` (`success`, `failed`, `reversed`, `never`), `transition-delay`, and `received`. Set a timeout `delay` above your client's read timeout. Delays are capped by `simulator.limits.max-delay` (2 minutes).

A profile can only be applied where the provider specification can express it. For example, NIBSS has no code for a reversed transaction, so `pending-then-reversed` is rejected for NIBSS transfers, and name enquiries cannot be pending. `GET /admin/providers` lists each operation's supported outcomes, query statuses, and match fields.

Selection precedence, highest first:

1. a one-time override for a provider reference;
2. an override for the next N requests of a provider operation (or of any operation of the provider);
3. a test-data rule on a request field, named as in the specification;
4. the operation profile;
5. the provider profile (applied only to operations that can express it);
6. the default profile, `happy-path`.

## Administration

The Admin API is the simulator control plane. It is not part of any provider contract.

Every `/admin/**` request requires `X-Admin-Token`. The default local token is `simulator`; override it with `SIMULATOR_ADMIN_TOKEN`, especially when the service is reachable by other users.

| Purpose | Endpoint |
|---|---|
| Providers, operations, capabilities, and assignments | `GET /admin/providers` |
| Profiles | `GET /admin/profiles` |
| Provider profile | `PUT` / `DELETE /admin/providers/{provider}/profile` |
| Operation profile | `PUT` / `DELETE /admin/providers/{provider}/operations/{operation}/profile` |
| Overrides | `POST` / `GET /admin/overrides`, `DELETE /admin/overrides/{id}` |
| Test-data rules | `POST` / `GET /admin/rules`, `DELETE /admin/rules/{id}` |
| Inspect a transaction | `GET /admin/transactions/{provider}/{reference}` |
| Reset | `POST /admin/state/reset` or `DELETE /admin/state` |

Make every NIBSS operation fail while Interswitch keeps succeeding:

```shell
curl --request PUT http://localhost:8080/admin/providers/nibss-nip/profile \
  --header 'X-Admin-Token: simulator' \
  --header 'Content-Type: application/json' \
  --data '{"profile": "failure"}'
```

Time out the next Interswitch transfer, whatever reference the client generates:

```shell
curl --request POST http://localhost:8080/admin/overrides \
  --header 'X-Admin-Token: simulator' \
  --header 'Content-Type: application/json' \
  --data '{"provider": "interswitch", "operation": "transfer", "profile": "timeout", "count": 1}'
```

Fail one known reference:

```shell
curl --request POST http://localhost:8080/admin/overrides \
  --header 'X-Admin-Token: simulator' \
  --header 'Content-Type: application/json' \
  --data '{"provider": "onafriq-bill-payment", "reference": "onafriq-airtime-100", "profile": "failure"}'
```

Fail every NIBSS name enquiry for one account number:

```shell
curl --request POST http://localhost:8080/admin/rules \
  --header 'X-Admin-Token: simulator' \
  --header 'Content-Type: application/json' \
  --data '{"provider": "nibss-nip", "operation": "name-enquiry", "field": "AccountNumber", "equals": "0000000001", "profile": "failure"}'
```

Reset between test runs:

```shell
curl --request POST http://localhost:8080/admin/state/reset \
  --header 'X-Admin-Token: simulator'
```

Reset clears transactions, overrides, and queued transitions, and restores the configured profiles and rules.

## Observability

`/actuator/health` and `/actuator/metrics` are exposed. `simulator.simulations` counts simulated operations by `provider`, `operation`, `profile`, `source`, and `outcome`; `simulator.duplicates` counts duplicate references; `simulator.held.requests` reports requests currently held by delays or timeouts. Logs record the same dimensions and never request field values.

## Storage and lifecycle

- Runtime transactions, overrides, and rules use thread-safe, size-bounded, in-memory stores; transactions and overrides expire.
- Provider and reference together form a transaction key.
- Concurrent creation of the same provider/reference is atomic; only one transaction is created and repeats receive the provider's duplicate response.
- A transaction is recorded when the request is received, so a client that times out and then queries sees the provider's recorded state.
- Capacity exhaustion returns a bare HTTP `503`; unmatched routes return a bare `404`.
- Restarting the process intentionally loses all runtime state and pending transitions.
- Multiple instances must not share stateful flows unless requests use sticky routing. No database or distributed cache is supported by design.

See `ARCHITECTURE.md` for the complete design and provider-onboarding path.
