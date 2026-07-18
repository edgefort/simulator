# Simulator

This project runs lightweight simulations of multiple payment providers in one Spring Boot application. It currently contains implementations for:

- NIBSS NIP v9.4 XML messages 010 through 023;
- Interswitch Quickteller Service Send Money v5 authentication, account-name enquiry, bank listing, transfer, and transaction query.

The application owns scenario selection, bounded in-memory transaction state, delays, timeouts, and pending transitions. No database, broker, cache, or embedded mock server is required.

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

The default port is `8080`. Docker Compose is deliberately disabled because this storage-free simulator has no database, broker, cache, or other required service.

## Swagger and OpenAPI

Open [Swagger UI](http://localhost:8080/swagger-ui.html) after startup to inspect and invoke provider and administration operations. The generated OpenAPI document is available at [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

Swagger UI enables **Try it out** by default. To call an administration operation, select **Authorize** and enter the configured admin token in `AdminToken`. The default local admin token is `local-admin`.

For Interswitch, first invoke `POST /passport/oauth/token` with HTTP Basic credentials and `grant_type=client_credentials`. Then select **Authorize**, enter the returned token in `InterswitchBearerToken`, and invoke the v5 operation. Enter only the token in Swagger; Swagger adds the `Bearer` prefix.

Run all tests:

```shell
./gradlew test
```

## Provider endpoints

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

Use `X-Simulation-Scenario` on a provider request to select a behavior for that request. If omitted, `success` is used. Swagger UI presents the supported values as a dropdown and preselects `success`.

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

Admin requests require `X-Admin-Token`. The default local token is `local-admin`; override it with the `SIMULATOR_ADMIN_TOKEN` environment variable.

| Operation | Endpoint |
|---|---|
| Provider metadata | `GET /admin/providers` |
| Profiles and active profile | `GET /admin/profiles` |
| Activate a profile | `PUT /admin/profiles/{profile}/activate` |
| Add a one-time reference override | `POST /admin/overrides` |
| Inspect a transaction | `GET /admin/transactions/{provider}/{reference}` |
| Reset memory and cancel queued events | `POST /admin/state/reset` or `DELETE /admin/state` |

Example one-time override:

```shell
curl --request POST http://localhost:8080/admin/overrides \
  --header 'Content-Type: application/json' \
  --header 'X-Admin-Token: local-admin' \
  --data '{
    "provider": "nibss-nip",
    "reference": "nip-reference-200",
    "scenario": "FAILURE"
  }'
```

An override has higher precedence than `X-Simulation-Scenario`, is consumed once, and expires from bounded memory.

## Storage and lifecycle

- Runtime transactions and overrides use thread-safe, size-bounded, TTL in-memory stores.
- Provider and reference together form a transaction key, so equal references from different providers remain isolated.
- Concurrent creation of the same provider/reference is atomic; only one transfer is created.
- Duplicate requests return provider-specific responses; Interswitch uses documented response code `90094`.
- Admin reset clears transactions and overrides and cancels queued transitions.
- Restarting the process intentionally loses all runtime state and pending events.
- Multiple instances must not share stateful flows unless requests use sticky routing. No database or distributed cache is supported by design.

See `ARCHITECTURE.md` for the complete design and provider-onboarding path.