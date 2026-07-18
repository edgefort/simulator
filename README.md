# Simulator

This project runs lightweight simulations of multiple payment providers in one Spring Boot application.

The application owns scenario selection, bounded in-memory transaction state, delays, timeouts, and pending transitions. No database, broker, cache, or embedded mock server is required.

## Available providers

| Provider | Admin provider ID | Implemented interface | Payload and security |
|---|---|---|---|
| NIBSS NIP | `nibss-nip` | NIP v9.4 XML messages 010 through 023 | XML over local HTTP; institution-specific WSDL and message security are not simulated because they were not supplied |
| Interswitch | `interswitch-transfer` | Quickteller Service Send Money v5 authentication, account-name enquiry, bank listing, transfer, and transaction query | JSON, OAuth client credentials, bearer authorization, `TerminalId`, and secure-data v12 `SHA-512` MAC validation |

Use `GET /admin/providers` to obtain the enabled status, specification version, and security mode configured for each provider at runtime. The provider IDs in this table are also used by transaction inspection and one-time admin overrides.

## Contract status

The NIBSS module implements the complete request/response XML pairs present in the supplied NIP v9.4 excerpt: name enquiry, direct-credit transfer, direct-debit transfer, transaction status query, balance enquiry, direct-credit advice, and direct-debit advice. The former provisional JSON endpoints have been removed. See `NIBSS_NIP_V9_4.md` for the exact scope and limitations.

This is not a claim of NIBSS certification. The supplied material does not include the institution-specific WSDL, service binding, endpoint address, or complete message-security specification, and it ends partway through message 025. The simulator therefore exposes the documented XML messages through local HTTP routes without inventing the missing SOAP binding or security rules.

The Interswitch module follows the public Quickteller Service Send Money v5 documentation reviewed on 18 July 2026. It implements the published OAuth client-credentials flow, bearer authorization, `TerminalId` and operation headers, transfer body casing, secure-data version 12 `SHA-512` MAC, response fields, and five-digit response codes. The public documentation contains contradictions and incomplete examples, so this implementation is not a claim of partner certification. See `INTERSWITCH_SEND_MONEY_V5.md` for the reviewed sources and exact limitations.

Before integrating a real consumer, confirm each provider module against the specification and credentials assigned to that consumer:

- endpoint paths and versions;
- request and response fields, formats, and codes;
- authentication, signatures, encryption, and certificate behavior;
- duplicate, requery, reversal, and callback rules.

The provider-neutral core does not need to change when those adapter contracts are corrected.

## Requirements and startup

- Java 25
- the included Gradle wrapper

Run the application:

```shell
./gradlew bootRun
```

The default port is `8080`. Override it with `SERVER_PORT`, for example:

```shell
SERVER_PORT=9090 ./gradlew bootRun
```

### Docker

The repository includes a multi-stage `Dockerfile`. It builds the application with Java 25, copies only the executable JAR into the runtime image, runs as the unprivileged `simulator` user, and defines an Actuator health check.

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

Runtime configuration can be supplied with `--env`, an environment file through `--env-file`, or the equivalent environment settings in the container platform:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port used by the service inside the container |
| `SIMULATOR_ADMIN_TOKEN` | `simulator` | Token required in `X-Admin-Token` for administration endpoints |
| `INTERSWITCH_CLIENT_ID` | `simulator-client` | OAuth client ID accepted by the simulated Interswitch token endpoint |
| `INTERSWITCH_CLIENT_SECRET` | `simulator-secret` | OAuth client secret accepted by the simulated Interswitch token endpoint |
| `INTERSWITCH_ACCESS_TOKEN` | `simulator-access-token` | Bearer token returned and accepted by the simulated Interswitch API |
| `INTERSWITCH_TOKEN_EXPIRES_IN` | `86400` | Simulated OAuth token lifetime in seconds |
| `INTERSWITCH_TERMINAL_ID` | `3PBL0001` | Terminal ID required by the simulated Interswitch API |
| `JAVA_TOOL_OPTIONS` | JVM default | Optional JVM options such as memory limits |

For example, place non-public local values in `~/.config/simulator/simulator.env`, outside the repository and image build context:

```dotenv
SIMULATOR_ADMIN_TOKEN=change-me
INTERSWITCH_CLIENT_ID=test-client
INTERSWITCH_CLIENT_SECRET=test-secret
INTERSWITCH_ACCESS_TOKEN=test-access-token
INTERSWITCH_TERMINAL_ID=3PBL0001
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
```

Then start the image with `docker run --rm --publish 8080:8080 --env-file ~/.config/simulator/simulator.env simulator:local`. Keep the file out of source control.

Do not use the local credential defaults outside isolated development. Docker Compose remains unnecessary because this storage-free simulator has no database, broker, cache, or other required service.

### Published image and versioning

Merging a pull request into the protected `main` branch runs the `Publish Docker image` GitHub Actions workflow. The workflow runs the tests, builds the repository `Dockerfile`, and publishes the image to GitHub Container Registry:

```text
ghcr.io/edgefort/simulator
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

Run all tests:

```shell
./gradlew test
```

## Available provider endpoints

| Provider | Operation | Endpoint |
|---|---|---|
| NIBSS NIP | Name enquiry (010/011) | `POST /nip/v9.4/nameenquirysingle` |
| NIBSS NIP | Direct-credit transfer (012/013) | `POST /nip/v9.4/fundtransfersingleitem_dc` |
| NIBSS NIP | Direct-debit transfer (014/015) | `POST /nip/v9.4/fundtransfersingleitem_dd` |
| NIBSS NIP | Transaction status query (016/017) | `POST /nip/v9.4/txnstatusquerysingleitem` |
| NIBSS NIP | Balance enquiry (018/019) | `POST /nip/v9.4/balanceenquiry` |
| NIBSS NIP | Direct-credit advice (020/021) | `POST /nip/v9.4/fundtransferAdvice_dc` |
| NIBSS NIP | Direct-debit advice (022/023) | `POST /nip/v9.4/fundtransferAdvice_dd` |
| Interswitch | Generate access token | `POST /passport/oauth/token` |
| Interswitch | Account-name enquiry | `GET /quicktellerservice/api/v5/transactions/DoAccountNameInquiry` |
| Interswitch | Fund-transfer banks | `GET /quicktellerservice/api/v5/configuration/fundstransferbanks` |
| Interswitch | Transfer | `POST /quicktellerservice/api/v5/transactions/Transfer` |
| Interswitch | Transaction query | `GET /quicktellerservice/api/v5/Transactions?requestRef={reference}` |

Example NIBSS transfer:

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

Generate the default local Interswitch access token:

```shell
curl --request POST 'http://localhost:8080/passport/oauth/token' \
  --user 'simulator-client:simulator-secret' \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data 'grant_type=client_credentials'
```

Example Interswitch transfer failure. The `MAC` is the lowercase `SHA-512` digest of `1250.50566CA1250.50566ACNG`, following secure-data version 12 field order:

```shell
curl --request POST http://localhost:8080/quicktellerservice/api/v5/transactions/Transfer \
  --header 'Authorization: Bearer simulator-access-token' \
  --header 'TerminalId: 3PBL0001' \
  --header 'Content-Type: application/json' \
  --header 'X-Simulation-Scenario: failure' \
  --data '{
    "InitiatingEntityCode": "PBL",
    "Initiation": {
      "Amount": 1250.50,
      "CurrencyCode": "566",
      "PaymentMethodCode": "CA",
      "Channel": "7"
    },
    "Termination": {
      "Amount": 1250.50,
      "CurrencyCode": "566",
      "PaymentMethodCode": "AC",
      "CountryCode": "NG",
      "AccountReceivable": {
        "AccountNumber": "0123456789",
        "AccountType": "00"
      },
      "EntityCode": "058"
    },
    "sender": {
      "lastname": "DOE",
      "othernames": "JANE"
    },
    "Beneficiary": {
      "lastname": "SMITH",
      "othernames": "JOHN"
    },
    "TransferCode": "isw-reference-100",
    "MAC": "afb1b1ca97390dc060e7c12aca6090ddb00b868c8f24c2e3ac49a03b88b161cc4f5d3a6f58c0be106e4d6527e410bd986d40ddd5348d37e89b949065e17d6a8f"
  }'
```

## Scenarios

Use `X-Simulation-Scenario` on a provider request to select a behavior for that request. If omitted, the currently active profile is used; the startup profile is `happy-path`, whose scenario is `success`. Swagger UI presents the supported values as a dropdown and preselects `success`.

| Value | Behavior |
|---|---|
| `success` | Immediate successful result |
| `failure` | Immediate provider-declared failure |
| `pending` | Pending result, then in-memory success |
| `delayed-success` | Wait for the configured delay, then succeed |
| `timeout` | Hold for the configured safety-bounded duration, then return HTTP `504` |
| `event-timeout` | Remain pending without a completion event |
| `late-event` | Remain pending until the longer configured transition delay, then complete |

Profile delays and limits are configured under `simulator` in `application.yaml`. Startup fails when a delay is negative or exceeds `simulator.limits.max-delay`. Concurrent held requests and queued completion events are bounded.

## Administration

The Admin API is the simulator control plane. It is used to discover configured providers, change default behavior, force a scenario for one reference, inspect temporary transaction state, and reset the simulator between tests. It is not part of either provider contract, and `X-Admin-Token` must not be sent to NIBSS or Interswitch endpoints.

Every `/admin/**` request requires `X-Admin-Token`. The default local token is `simulator`; override it with `SIMULATOR_ADMIN_TOKEN`, especially when the service is reachable by other users. In Swagger UI, select **Authorize**, enter only the token in `AdminToken`, and then invoke an administration operation.

| Purpose | Endpoint | Result |
|---|---|---|
| Discover providers | `GET /admin/providers` | Lists provider IDs, enabled state, specification version, and security mode |
| Discover profiles | `GET /admin/profiles` | Returns the active profile and all available profiles |
| Change default behavior | `PUT /admin/profiles/{profile}/activate` | Makes the selected profile apply when a provider request has no scenario header or override |
| Force one reference | `POST /admin/overrides` | Stores a TTL-bound, one-time provider/reference scenario override |
| Inspect temporary state | `GET /admin/transactions/{provider}/{reference}` | Returns the in-memory transaction or HTTP `404` when it does not exist or has expired |
| Return to a clean state | `POST /admin/state/reset` or `DELETE /admin/state` | Clears transactions and overrides and cancels queued transitions |

List the available providers:

```shell
curl http://localhost:8080/admin/providers \
  --header 'X-Admin-Token: simulator'
```

Activate the failure profile for provider requests that do not explicitly select a scenario:

```shell
curl --request PUT http://localhost:8080/admin/profiles/failure/activate \
  --header 'X-Admin-Token: simulator'
```

Example one-time override:

```shell
curl --request POST http://localhost:8080/admin/overrides \
  --header 'Content-Type: application/json' \
  --header 'X-Admin-Token: simulator' \
  --data '{
    "provider": "nibss-nip",
    "reference": "nip-reference-200",
    "scenario": "FAILURE"
  }'
```

Use `nibss-nip` or `interswitch-transfer` as the provider value. The transaction reference is the NIBSS `SessionID` or the Interswitch transfer reference used by the simulated operation.

Scenario selection precedence is: one-time admin override, `X-Simulation-Scenario`, then the active profile. An override is consumed once and expires from bounded memory if it is not used. Profiles remain active until changed or the application restarts; all transaction and override state is intentionally ephemeral.

Reset state between test runs when a clean simulator is required:

```shell
curl --request POST http://localhost:8080/admin/state/reset \
  --header 'X-Admin-Token: simulator'
```

## Storage and lifecycle

- Runtime transactions and overrides use thread-safe, size-bounded, TTL in-memory stores.
- Provider and reference together form a transaction key, so equal references from different providers remain isolated.
- Concurrent creation of the same provider/reference is atomic; only one transfer is created.
- Duplicate requests return provider-specific responses; Interswitch uses documented response code `90094`.
- Admin reset clears transactions and overrides and cancels queued transitions.
- Restarting the process intentionally loses all runtime state and pending events.
- Multiple instances must not share stateful flows unless requests use sticky routing. No database or distributed cache is supported by design.

See `ARCHITECTURE.md` for the complete design and provider-onboarding path.