# Interswitch Quickteller Service Send Money v5

## Scope and review date

This implementation was built from Interswitch's public Send Money documentation and API references, reviewed on 18 July 2026. It covers:

- OAuth client-credentials access-token generation;
- account-name enquiry;
- fund-transfer bank listing;
- single fund transfer using secure-data version 12;
- transaction query by request reference;
- documented five-digit response codes relevant to these operations.

Public documentation is evidence for the HTTP contract but is not a substitute for partner onboarding, assigned credentials, QA certification, or a private product specification.

## Reviewed official sources

- [Send Money overview](https://docs.interswitchgroup.com/docs/overview-3)
- [Generate access token](https://docs.interswitchgroup.com/reference/generate-access-token-1)
- [OAuth 2.0 authentication](https://docs.interswitchgroup.com/docs/oauth-20-authentication)
- [Single Transfer](https://docs.interswitchgroup.com/docs/single-transfer-1)
- [Do Account Name Enquiry](https://docs.interswitchgroup.com/reference/do-account-name-enquiry)
- [Get Banks Code](https://docs.interswitchgroup.com/reference/get-banks-code)
- [Get Bank Codes narrative guide](https://docs.interswitchgroup.com/docs/get-bank-codes)
- [Perform a Fund Transfer](https://docs.interswitchgroup.com/reference/perform-a-fund-transfer)
- [Perform Transaction Query](https://docs.interswitchgroup.com/reference/perform-transaction-query)
- [Response Codes](https://docs.interswitchgroup.com/docs/response-codes)

The overview links the five implemented operations. Broader portal pages for unrelated products were not used to invent Send Money fields or behavior.

## Implemented contract

| Operation | Local simulator path | Required authorization and inputs |
|---|---|---|
| Access token | `POST /passport/oauth/token` | HTTP Basic Base64 `clientId:secret`, form field `grant_type=client_credentials` |
| Name enquiry | `GET /quicktellerservice/api/v5/transactions/DoAccountNameInquiry` | Bearer token; `TerminalId`, `bankCode`, and `accountId` headers |
| Banks | `GET /quicktellerservice/api/v5/configuration/fundstransferbanks` | Bearer token and `TerminalId` header |
| Transfer | `POST /quicktellerservice/api/v5/transactions/Transfer` | Bearer token, `TerminalId`, and documented JSON body |
| Query | `GET /quicktellerservice/api/v5/Transactions?requestRef={reference}` | Bearer token, `TerminalId`, and request reference |

The transfer `MAC` is validated as lowercase or uppercase hexadecimal `SHA-512` over this secure-data version 12 concatenation, with no separator:

```text
Initiation.Amount
+ Initiation.CurrencyCode
+ Initiation.PaymentMethodCode
+ Termination.Amount
+ Termination.CurrencyCode
+ Termination.PaymentMethodCode
+ Termination.CountryCode
```

Amounts use their JSON decimal representation when the simulator constructs the MAC source. For example, `10000` contributes `10000`, while `1250.50` contributes `1250.50`.

## Simulator credentials and controls

Defaults are intentionally local and non-production:

| Environment variable | Default |
|---|---|
| `INTERSWITCH_CLIENT_ID` | `simulator-client` |
| `INTERSWITCH_CLIENT_SECRET` | `simulator-secret` |
| `INTERSWITCH_ACCESS_TOKEN` | `simulator-access-token` |
| `INTERSWITCH_TOKEN_EXPIRES_IN` | `86400` |
| `INTERSWITCH_TERMINAL_ID` | `3PBL0001` |

The token endpoint is stateless: it returns the configured opaque simulator token and does not persist issued tokens. The v5 API validates that configured bearer value. This preserves the project's no-database, no-cache objective while exercising the documented client flow.

`X-Simulation-Scenario` is a simulator-only optional header and is not part of the Interswitch provider contract. It selects success, failure, pending, delay, timeout, event-timeout, or late completion. If omitted, success is used. Transaction state remains bounded, TTL-based, and in memory.

## Public-documentation inconsistencies and limits

The implementation does not silently treat contradictory examples as authoritative:

- Reference OpenAPI metadata labels some v5 operations as HTTP Basic, while the operation examples and OAuth guide require bearer authorization. The simulator follows the bearer flow documented in the examples and OAuth guide.
- The bank reference publishes `/configuration/fundstransferbanks (COPY)`. The narrative guide publishes `/configuration/fundstransferbanks`; the simulator uses the narrative path.
- The transaction-query reference embeds a sample query in its displayed path, alternates between `requestReferencevalue` and `requestRef`, and shows a bill-payment response. The simulator uses `GET /Transactions?requestRef=...`, matching the Send Money curl example, and returns the transfer response shape.
- The Single Transfer guide contains an `Authentication` header in one example and `Authorization` elsewhere. The simulator uses the standard `Authorization: Bearer ...` header.
- The public docs do not define the transaction-reference generation algorithm. The simulator uses the caller's unique `TransferCode` as the in-memory reference and exposes it as `TransactionReference`, avoiding an invented provider algorithm.
- The public transfer contract does not define a callback URL. Interswitch pending and late-event states are observed through transaction query; no undocumented callback field is added.
- The bank response is the six-entry example published by the narrative guide, not a claim to be the complete current production bank directory.

Before certification use, verify these points against the exact Interswitch product/version and partner documentation assigned to the integrating organization.