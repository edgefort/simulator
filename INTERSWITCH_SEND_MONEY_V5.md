# Interswitch Quickteller Service Send Money v5

## Scope and review date

Provider id: **`interswitch`** (shared with Bills Payment; see `INTERSWITCH_BILLS_PAYMENT_V5.md`). Quickteller Service v5 is one API host with one Passport OAuth flow, one `TerminalId`, and one `GET /Transactions?requestRef=` query, so Send Money and Bills Payment are one simulator provider with one transaction keyspace.

Built from Interswitch's public documentation, re-reviewed on 7 November 2026. It covers:

- OAuth client-credentials access-token generation;
- account-name enquiry;
- fund-transfer bank listing;
- single fund transfer using secure-data version 12;
- transaction query by request reference (shared with Bills Payment).

Public documentation is evidence for the HTTP contract but is not a substitute for partner onboarding, assigned credentials, QA certification, or a private product specification.

## Reviewed official sources

- [Send Money overview](https://docs.interswitchgroup.com/docs/overview-3)
- [Single Transfer guide](https://docs.interswitchgroup.com/docs/single-transfer) — name-enquiry and transfer samples, MAC formula
- [Single Transfer API reference](https://docs.interswitchgroup.com/reference/fund-transfer) — `POST /transactions/TransferFunds`, response schema
- [Name Enquiry By Account reference](https://docs.interswitchgroup.com/reference/name-inquiry) — `POST /Transactions/DoAccountNameInquiry`, response schema
- [Resolve Bank Code guide](https://docs.interswitchgroup.com/docs/get-banks-code) and [Get Bank Codes reference](https://docs.interswitchgroup.com/reference/bank-code-copy)
- [Query Transaction reference](https://docs.interswitchgroup.com/reference/query-transaction-1) (linked from the Send Money overview)
- [Response Codes (Quickteller v5 / VAS)](https://docs.interswitchgroup.com/docs/response-codes)
- [Bills Payment guide](https://docs.interswitchgroup.com/docs/bills-payment-1) — Passport token request/response, query status values

The previously cited `reference/perform-a-fund-transfer`, `reference/do-account-name-enquiry`, and `reference/perform-transaction-query` pages no longer exist (HTTP 404 on 7 November 2026). The simulator now follows the pages above; the former `POST /transactions/Transfer` route and PascalCase transfer body were removed because no current page documents them.

## Implemented contract

| Operation | Path | Authorization and inputs |
|---|---|---|
| Access token | `POST /passport/oauth/token` | HTTP Basic Base64 `clientId:secret`, form field `grant_type=client_credentials` (`scope` accepted) |
| Name enquiry | `GET /quicktellerservice/api/v5/transactions/DoAccountNameInquiry` (guide) and `POST /quicktellerservice/api/v5/Transactions/DoAccountNameInquiry` (reference) | Bearer token; `TerminalId`, `bankCode`, `accountId` headers |
| Banks | `GET /quicktellerservice/api/v5/configuration/fundstransferbanks` | Bearer token and `TerminalId` |
| Transfer | `POST /quicktellerservice/api/v5/transactions/TransferFunds` | Bearer token, `TerminalId`, camelCase JSON body exactly as in the Single Transfer guide |
| Query | `GET /quicktellerservice/api/v5/Transactions?requestRef={transferCode}` | Bearer token and `TerminalId` |

When `simulator.providers.interswitch.base-path` is set (for example `/isw`), every path above, including the token endpoint, is served under that prefix; bearer and `TerminalId` checks still apply.

Responses use only documented fields:

- name enquiry: `AccountName`, `ResponseCode`, `ResponseCodeGrouping` (`AccountName` omitted on failure);
- banks: `{"banks":[{"id","cbnCode","bankName","bankCode"}]}` with the six published sample banks;
- transfer: `MAC` (echo of the request `mac`), `TransactionDate`, `TransactionReference`, `Pin` (`null`), `TransferCode` (`null`), `ResponseCode`, `ResponseCodeGrouping`;
- query of a transfer: the published Query Transaction fields a transfer can populate — `amount`, `currencyCode`, `customer` (beneficiary account), `customerEmail`/`customerMobile` (sender, when supplied), `paymentDate`, `requestReference`, `status`, `transactionRef`, `transactionResponseCode`.

The transfer `mac` is validated as lowercase or uppercase hexadecimal `SHA-512` over this secure-data version 12 concatenation, with no separator:

```text
initiation.amount + initiation.currencyCode + initiation.paymentMethodCode
+ termination.amount + termination.currencyCode + termination.paymentMethodCode + termination.countryCode
```

Amounts contribute their JSON representation (`"100000"` contributes `100000`; `1250.50` contributes `1250.50`).

## Simulated operations

Behavior is selected only through the admin API or configuration (reference override → operation override → test-data rule → operation profile → provider profile → default profile). Provider requests carry no simulator parameters: the former simulator-only scenario request header has been removed, and no simulator-only response code or HTTP status is ever returned.

| Operation | Kind | Outcomes | Query statuses | Rule match fields |
|---|---|---|---|---|
| `name-enquiry` | stateless | success, failure, timeout | — | `bankCode`, `accountId` |
| `transfer` | transactional (reference = `transferCode`) | success, failure, pending, timeout | pending, success, failed, reversed | `initiatingEntityCode`, `initiation.amount`, `termination.amount`, `termination.entityCode`, `termination.accountReceivable.accountNumber` |

Profile support: every profile in `application.yaml` applies to `transfer` (`pending-then-reversed` is reported as query status `Reversed`). `name-enquiry` rejects `pending*` profiles because the specification has no pending enquiry response. `timeout*` profiles close the connection without any bytes after the profile delay; the transfer is recorded as configured (`timeout-not-received` records nothing, so the query reports `70018`).

## Response codes

| Situation | HTTP | `ResponseCode` | `ResponseCodeGrouping` | Source |
|---|---|---|---|---|
| Name enquiry / transfer success | 200 | `90000` | `SUCCESSFUL` | Single Transfer guide samples; Response Codes |
| Name enquiry failure | 200 | `40100` Failed Account Name Inquiry | `FAILED` | Response Codes (Failed) |
| Transfer failure | 200 | `90096` System Malfunction | `FAILED` | Response Codes (Failed) |
| Transfer pending | 200 | `90009` Request in progress | `PENDING` | Response Codes (Exception / Pending) |
| Duplicate `transferCode` | 200 | `30020` Duplicate Transfer Code | `FAILED` | Response Codes (Failed) |
| Query status `Completed` / `Pending` / `Failed` | 200 | `transactionResponseCode` `90000` / `90009` / `90096` | — | Query Transaction reference; Bills Payment guide status table |
| Query status `Reversed` | 200 | `transactionResponseCode` omitted | — | Bills Payment guide status table |

Rejections use the Quickteller v5 response envelope `{"ResponseCode","ResponseDescription","ResponseCodeGrouping":"FAILED"}` with codes and descriptions from the Response Codes page:

| Rejection | HTTP | `ResponseCode` |
|---|---|---|
| Missing `Authorization` | 401 | `10003` Unauthorized |
| Invalid or non-bearer token | 401 | `60003` Security token invalid or expired. |
| Missing `TerminalId` | 400 | `20021` TerminalId/ClientId is required |
| `TerminalId` not the configured terminal | 400 | `70012` Unrecognized terminal owner |
| MAC mismatch | 400 | `20050` Message integrity validation failed |
| Missing/invalid field or header, malformed JSON, wrong media type | 400 | `10002` Invalid Request |
| Query reference not found | 400 | `70018` Transaction Reference not found |

Token endpoint errors use the OAuth 2.0 error body: `401 {"error":"invalid_client"}`, `400 {"error":"invalid_request"}` (no `grant_type`), `400 {"error":"unsupported_grant_type"}`.

## Simulator credentials

| Environment variable | Default |
|---|---|
| `INTERSWITCH_CLIENT_ID` | `simulator-client` |
| `INTERSWITCH_CLIENT_SECRET` | `simulator-secret` |
| `INTERSWITCH_ACCESS_TOKEN` | `simulator-access-token` |
| `INTERSWITCH_TOKEN_EXPIRES_IN` | `86400` |
| `INTERSWITCH_TERMINAL_ID` | `3PBL0001` |
| `INTERSWITCH_BASE_PATH` | empty |

These bind to `simulator.providers.interswitch.config.*`. The token endpoint is stateless and returns the configured opaque token; the v5 API accepts only that bearer value and only the configured `TerminalId`.

## Public-documentation inconsistencies and limits

- Reference OpenAPI metadata labels the Send Money operations as HTTP Basic, while the guides require bearer authorization. The simulator follows the bearer flow.
- Name enquiry is published as `GET /transactions/DoAccountNameInquiry` in the guide and as `POST /Transactions/DoAccountNameInquiry` in the reference; both routes are served. Header names differ only in case (`bankCode`/`bankcode`), which HTTP treats as equal.
- The Single Transfer guide example sends `Authentication: Bearer` and `terminalId: 3PBL`; the simulator requires the standard `Authorization: Bearer …` header and the configured terminal.
- The transfer body is accepted only in the camelCase published by the guide; no published page shows another casing for it.
- The public docs do not define the `TransactionReference` generation algorithm (the sample is `PBL|LOC|CA|ABP|AC|…`). The simulator returns the caller's `transferCode` as `TransactionReference` and as the query `transactionRef`.
- `TransactionDate` uses `dd/MMM/yyyy HH:mm:ss` in Africa/Lagos time; the published sample (`03/Feb/2023 03:47:064`) has an irregular seconds field.
- The Query Transaction reference publishes only a bill-payment response. For transfers the simulator returns that same envelope with the fields a transfer can populate and omits `BillPayment`, `serviceCode`, `serviceName`, `serviceProviderId`, `surcharge`, and `transactionSet`, whose transfer values are not published.
- No `transactionResponseCode` is published for a reversed transaction, so it is omitted when `status` is `Reversed`.
- The Response Codes page lists both `90094` (Duplicate Transaction) and `30020` (Duplicate Transfer Code); the simulator uses `30020` because it specifically names the transfer code.
- No error body is published for authentication, terminal, validation, MAC, or not-found rejections, and the references publish only HTTP 200 and 400 (`{}`) responses. The simulator uses the closest published envelope (`ResponseCode`/`ResponseDescription`/`ResponseCodeGrouping`) with codes from the Response Codes page, HTTP 400 for request errors, and HTTP 401 for bearer-token failures.
- Passport token errors are not published; the simulator uses the OAuth 2.0 (RFC 6749 §5.2) error body.
- The `90009` pending code comes from the Quickteller v5 Response Codes page; the Send Money pages publish no transfer-specific pending example.
- The public transfer contract defines no callback; pending states are observed through the query.
- The bank response is the six-entry published example, not the production bank directory.

Before certification use, verify these points against the exact Interswitch product/version and partner documentation assigned to the integrating organization.
