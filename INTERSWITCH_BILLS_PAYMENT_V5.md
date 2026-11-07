# Interswitch Quickteller Bills Payment v5

## Source and scope

Provider id: **`interswitch`** (shared with Send Money; see `INTERSWITCH_SEND_MONEY_V5.md`). Quickteller Service v5 is one API host with shared Passport OAuth, one `TerminalId`, and one `GET /Transactions?requestRef=` query, so bill payments and transfers share one transaction keyspace: a `requestReference` already used by a transfer `transferCode` is a duplicate.

Implemented from the public [Bills Payment guide](https://docs.interswitchgroup.com/docs/bills-payment-1) and [Airtime Recharge (Virtual Top up) guide](https://docs.interswitchgroup.com/docs/airtime-recharge-virtual-top-up) (both document version 2.0, updated 29 September 2026), the [Query Transaction reference](https://docs.interswitchgroup.com/reference/query-transaction-1), and the [Response Codes](https://docs.interswitchgroup.com/docs/response-codes) page, re-reviewed on 7 November 2026. The module covers the six-step flow:

1. retrieve categories;
2. retrieve billers (by category or all);
3. retrieve payment items;
4. validate customers;
5. make a bill payment or airtime recharge;
6. query a transaction.

Airtime is not a separate route: the Airtime guide uses the same `POST /Transactions` with category `4` (`Mobile/Recharge`) payment codes.

## Authentication and headers

- `POST /passport/oauth/token` accepts HTTP Basic client credentials and `grant_type=client_credentials`;
- every Quickteller v5 route requires `Authorization: Bearer <token>` and the configured `TerminalId` header (header names are case-insensitive, so the guides' `TerminalID`/`terminalId` spellings work);
- customer validation requires `TerminalId` in the body (as published); payment accepts an optional body `TerminalId` (the Airtime guide includes it, the Bills Payment guide does not). A body `TerminalId` must equal the header.

When `simulator.providers.interswitch.base-path` is set (for example `/isw`), every route, including the token endpoint, is served under that prefix and the same checks apply.

Credentials: `INTERSWITCH_CLIENT_ID`, `INTERSWITCH_CLIENT_SECRET`, `INTERSWITCH_ACCESS_TOKEN`, `INTERSWITCH_TOKEN_EXPIRES_IN`, `INTERSWITCH_TERMINAL_ID`, `INTERSWITCH_BASE_PATH` (bound to `simulator.providers.interswitch.*`).

## Implemented routes

| Method | Route | Behavior |
|---|---|---|
| `GET` | `/quicktellerservice/api/v5/services/categories` | Utility and mobile/recharge categories |
| `GET` | `/quicktellerservice/api/v5/services?categoryId={id}` and `/services` | Billers of one category, or of all categories |
| `GET` | `/quicktellerservice/api/v5/services/options?serviceid={id}` | Payment items of a biller |
| `POST` | `/quicktellerservice/api/v5/Transactions/validatecustomers` | Validates the `customers` entries |
| `POST` | `/quicktellerservice/api/v5/Transactions` (Airtime guide, reference) and `/transactions` (Bills Payment guide sample) | Bill or airtime purchase |
| `GET` | `/quicktellerservice/api/v5/Transactions?requestRef={reference}` | Shared query for bill payments and transfers |

Deterministic catalog:

| Service | Category | Biller ID | Payment code | Amount rule | Result |
|---|---:|---:|---|---|---|
| Abuja Disco Buy Power Prepaid | `1` | `17589` | `051758901` | At least `35000` kobo | Pin-bearing response |
| MTN e-Charge Prepaid | `4` | `109` | `628051043` | `5000`–`1000000` kobo | Network recharge metadata |
| 9mobile/Etisalat Recharge Top-Up | `4` | `120` | `6280510490` | `5000`–`1000000` kobo | Network recharge metadata |
| Glo QuickCharge | `4` | `402` | `628051045` | `5000`–`1000000` kobo | Network recharge metadata |

## Payment example

```json
{
  "paymentCode": "628051043",
  "customerId": "2348012345678",
  "customerMobile": "2348012345678",
  "customerEmail": "customer@example.com",
  "amount": "50000",
  "requestReference": "145300000001"
}
```

Amounts are in kobo; `requestReference` is required, unique across the whole `interswitch` provider, and at most 20 characters. Optional `customerName`, `additionalInfo`, and `TerminalId` are accepted. Successful airtime responses include `network`, `phoneNumber`, and naira `amountCredited` in `AdditionalInfo`; the pin-based electricity product returns `RechargePIN`, `PhcnTokenDetails`, `MiscData`, and `Pin`/`Token`/`Units` in `AdditionalInfo`.

The query returns the published shape: `BillPayment` (`biller`, `customerId1`, `customerId2`, `paymentTypeName`, `paymentTypeCode`, `billerId`), `amount`, `currencyCode`, `customer`, `customerEmail`, `customerMobile`, `paymentDate`, `requestReference`, `serviceCode`, `serviceName`, `serviceProviderId`, `status`, `surcharge`, `transactionRef`, `transactionResponseCode`, `transactionSet` (`BillPayment`). It is answered from the recorded simulator transaction (payment code, customer, mobile, email, amount, terminal, `TransactionRef`, recharge PIN are stored as transaction attributes).

## Simulated operations

Behavior is selected only through the admin API or configuration (reference override → operation override → test-data rule → operation profile → provider profile → default profile). The former simulator-only scenario request header and the simulator-only timeout response code/HTTP 504 have been removed; a timeout is now a dropped connection with no response bytes.

| Operation | Kind | Outcomes | Query statuses | Rule match fields |
|---|---|---|---|---|
| `customer-validation` | stateless, evaluated once per `customers` entry with a known payment code | success, failure, timeout | — | `PaymentCode`, `CustomerId` |
| `bill-payment` | transactional (reference = `requestReference`) | success, failure, pending, timeout | pending, success, failed, reversed | `paymentCode`, `customerId`, `customerMobile`, `amount` |

Profile support: every profile applies to `bill-payment` (`pending-then-reversed` is reported as query status `Reversed`); `customer-validation` rejects `pending*` profiles. `timeout*` profiles close the connection without any bytes after the profile delay; the payment is recorded as configured, and `timeout-not-received` records nothing (query returns `70018`). Catalog lookups are not simulated operations and always succeed. Unknown payment codes and out-of-range amounts are rejected before any simulation and are not recorded.

## Response codes

| Situation | HTTP | `ResponseCode` | Grouping | Source |
|---|---|---|---|---|
| Success (catalog, validation, payment) | 200 | `90000` Success | `SUCCESSFUL` | Bills Payment and Airtime guides |
| Customer validation failure | 200 | customer `90002` Customer not found; top level `90008` Validation failed | `FAILED` | Bills Payment / Airtime response-code tables |
| Unknown payment code | 200 | `90007` Invalid payment code (customer level in validation, top level `90008`) | `FAILED` | Bills Payment / Airtime tables |
| Amount outside product range | 200 | `90003` Invalid amount | `FAILED` | Bills Payment / Airtime tables |
| Duplicate `requestReference` | 200 | `90005` Duplicate transaction | `FAILED` | Bills Payment / Airtime tables |
| Payment failure | 200 | `90006` Service unavailable | `FAILED` | Bills Payment / Airtime tables |
| Payment pending | 200 | `90009` Request in progress | `PENDING` | Response Codes (Exception / Pending; page scope: Bills Payment, Airtime & Data) |
| Query `Completed` / `Pending` / `Failed` | 200 | `transactionResponseCode` `90000` / `90009` / `90006` | — | Query Transaction reference; guides' status tables |
| Query `Reversed` | 200 | `transactionResponseCode` omitted | — | Guides' status tables |

Rejections use the Quickteller v5 envelope `{"ResponseCode","ResponseDescription","ResponseCodeGrouping":"FAILED"}` with codes from the Response Codes page: `401` `10003` (missing `Authorization`), `401` `60003` (invalid token), `400` `20021` (missing `TerminalId`), `400` `70012` (unknown terminal, or body `TerminalId` differing from the header), `400` `10002` Invalid Request (missing/invalid fields or parameters, malformed JSON, unsupported media type), `400` `70018` Transaction Reference not found (query). Token errors use the OAuth 2.0 error body (`invalid_client`, `invalid_request`, `unsupported_grant_type`).

## Public-documentation limitations

- This is an implementation of the reviewed public guides, not a claim of Interswitch certification or production catalog parity.
- The Bills Payment guide shows the payment body in camelCase in its main example and in PascalCase (`CustomerId`, `PaymentCode`, `Amount`, `RequestReference`, …) in its "Passing Additional Information" example, and its curl samples use both `/Transactions` and `/transactions`. The payment body therefore binds property names case-insensitively and both path spellings are served. Customer validation, whose fields are published in one casing only, binds exactly.
- `90009`: the Response Codes page (scope: Bills Payment, Airtime & Data) defines it as pending "Request in progress"; the Airtime guide's table describes it as "Network error". The simulator follows the Response Codes page.
- `90005`: the Bills Payment and Airtime tables define it as "Duplicate transaction"; the generic Response Codes page lists "Do not honor" and uses `90094` for duplicates. The simulator follows the flow-specific tables.
- No top-level code is published for a validation response containing an invalid customer; the simulator uses `90008` Validation failed.
- No `transactionResponseCode` is published for a reversed transaction, so it is omitted when `status` is `Reversed`. No reversal request operation is published, so none is simulated.
- The Query Transaction reference names the nested object `billPayment`; the Bills Payment and Airtime guides use `BillPayment`. The simulator follows the guides.
- No error body is published for authentication, terminal, validation, or not-found rejections, and the references publish only HTTP 200 and 400 (`{}`). The simulator uses the closest published envelope with Response Codes page codes, HTTP 400 for request errors, and HTTP 401 for bearer failures.
- `TransactionRef` follows the published `PBL|Web|{TerminalId}|{ShortName}|…` pattern with the `requestReference` as the final segment; the real generation algorithm is not published. `paymentDate` uses the query sample format `M/d/yyyy h:mm:ss a` in Africa/Lagos time.
- The Abuja Disco example reports `AmountType` `2` and amount `0` while validation describes a `35000` kobo minimum; the simulator accepts amounts at or above `35000`.
- The Airtime guide lists MTN with `AmountType` `2` while defining regular airtime as ₦50–₦10,000; the simulator applies that range and reports `AmountType` `0`.
- Real billers, fees, customer records, balances, and token values are deterministic simulation data. Partner network controls, certificates, reconciliation, and settlement are not simulated.
