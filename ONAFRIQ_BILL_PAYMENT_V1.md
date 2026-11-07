# Onafriq Biller Aggregation Platform API v1.0.0

## Source and scope

This module follows the public Onafriq B2B VAS ("VAS Products API") project at <https://developers.onafriq.com/docs/b2b-vas/52r1qylqo22f2-overview>, re-reviewed on 7 November 2026 from the published articles (Integration Flow → Request/Response/Requery, Baxi Authentication, every service page) and the exported OpenAPI service `Onafriq Biller Aggregation Platform` version `1.0.0` (servers `https://api.staging.baxibap.com/services` and production, security scheme `ApiKeyAuth` = header `x-api-key`).

The project also documents fund transfer, international airtime/remittance, vehicle papers, and car-insurance policy extension/info. This module implements the bill-payment subset listed below only.

Where the prose and the exported OpenAPI disagree, the OpenAPI path and request schema win, except where noted under [Assumptions and gaps](#assumptions-and-gaps).

Provider id: `onafriq-bill-payment`. All paths below are relative to the client's Onafriq base URL. With `simulator.providers.onafriq-bill-payment.base-path=/onafriq` they are served under `/onafriq/services/...`; authentication and error handling still apply because they are scoped to the module's handlers, not to URL prefixes.

## Authentication

Every route requires one of the documented API-key forms:

- `x-api-key: <key>`
- `Authorization: Api-key <key>`

The accepted key is `simulator.providers.onafriq-bill-payment.config.api-key` (`ONAFRIQ_API_KEY`, default `simulator-api-key`).

| Case | HTTP | Body |
|---|---|---|
| Neither header present | `401` | `{"status":"error","message":"Bearer Token not provided","code":"BX0013"}` |
| Header present but the key does not match | `401` | `{"status":"error","message":"Bad credentials","code":"SEC00001"}` |

Codes and messages are the Response page code table entries (`BX0013 Bearer Token not provided`, `SEC00001 Bad credentials`); `401 - Unauthorized / Authentication / Authorization Issues` is the documented HTTP status.

**HMAC (Baxi) authentication is pending.** The Baxi HMAC mode (`Authorization: Baxi <username>:<signature>` plus `Baxi-date`) is not simulated until the user supplies the authoritative signing specification for the consuming organization. Until then an HMAC `Authorization` header without a valid API key is rejected with `SEC00001`.

## Routes

| Capability | Route | Simulator operation |
|---|---|---|
| Wallet balance | `GET /services/superagent/account/balance` | static |
| Transaction query | `GET /services/superagent/transaction/query?agentReference=…` | reads state |
| Transaction requery | `GET /services/superagent/transaction/requery?agentReference=…` | reads state |
| Biller categories | `GET /services/billers/category/all` | static |
| All billers | `GET /services/billers/services/list` | static |
| Billers by category | `POST /services/billers/services/category` | static |
| Airtime | `GET /services/airtime/providers` | static |
| | `POST /services/airtime/request` | `airtime` |
| Data bundles | `GET /services/databundle/providers` | static |
| | `POST /services/databundle/bundles` | `data-bundles` |
| | `POST /services/databundle/request` | `data-bundle` |
| Cable TV | `GET /services/cabletv/providers` | static |
| | `POST /services/multichoice/list` | `cable-tv-bouquets` |
| | `POST /services/multichoice/addons` | `cable-tv-addons` |
| | `POST /services/multichoice/request` | `cable-tv` |
| Electricity | `GET /services/electricity/billers` | static |
| | `POST /services/electricity/verify` (deprecated by Onafriq) | `electricity-verify` |
| | `POST /services/electricity/request` | `electricity` |
| E-pin | `GET /services/epin/providers` | static |
| | `POST /services/epin/bundles` | `epin-bundles` |
| | `POST /services/epin/request` | `epin` |
| JAMB / exam pins | `POST /services/exampin/purchase` | `jamb` |
| Betting | `GET /services/betting/billers` | static |
| | `POST /services/betting/validate` | `betting-validate` |
| | `POST /services/betting/request` | `betting` |
| Account finder | `POST /services/namefinder/query` | `name-finder` |
| Vehicle insurance | `POST /services/carinsurance/request` | `vehicle-insurance` |

Request field names keep the documented casing (`service_type`, `account_number`, `smartcard_number`, `product_code`, `product_monthsPaidFor`, `total_amount`, `agentId`, `agentReference`, `pinValue`, `numberOfPins`, ...). Required fields follow the OpenAPI `required` lists.

## Simulated operations

Behavior is selected only through the admin API or configuration (reference override → operation override → test-data rule → operation profile → provider profile → default profile). Clients send exactly the requests they would send to Onafriq.

### Purchases (transactional, keyed by `agentReference`)

Outcomes: `SUCCESS`, `FAILURE`, `PENDING`, `TIMEOUT`. Query statuses: `PENDING`, `SUCCESS`, `FAILED`. `REVERSED` is not declared because the documentation defines no reversed representation.

| Operation | Route | Match fields |
|---|---|---|
| `airtime` | `/airtime/request` | `service_type`, `plan`, `phone`, `amount`, `agentReference` |
| `data-bundle` | `/databundle/request` | `service_type`, `datacode`, `phone`, `amount`, `agentReference` |
| `cable-tv` | `/multichoice/request` | `service_type`, `smartcard_number`, `product_code`, `total_amount`, `agentReference` |
| `electricity` | `/electricity/request` | `service_type`, `account_number`, `phone`, `amount`, `agentReference` |
| `epin` | `/epin/request` | `service_type`, `amount`, `pinValue`, `numberOfPins`, `agentReference` |
| `jamb` | `/exampin/purchase` | `service_type`, `account_number`, `product_code`, `phone`, `amount`, `agentReference` |
| `betting` | `/betting/request` | `service_type`, `account_number`, `action`, `amount`, `agentReference` |
| `vehicle-insurance` | `/carinsurance/request` | `service_type`, `client.phone`, `vehicle.registrationNumber`, `order.amount`, `agentReference` |

### Validations (stateless)

Outcomes: `SUCCESS`, `FAILURE`, `TIMEOUT`.

| Operation | Route | Match fields |
|---|---|---|
| `name-finder` | `/namefinder/query` | `service_type`, `account_number`, `product_code` |
| `electricity-verify` | `/electricity/verify` | `service_type`, `account_number` |
| `betting-validate` | `/betting/validate` | `service_type`, `account_number` |

### Catalog lookups answered by the upstream biller (stateless)

Outcomes: `SUCCESS`, `TIMEOUT` (no failure code is documented for these lookups).

| Operation | Route | Match fields |
|---|---|---|
| `data-bundles` | `/databundle/bundles` | `service_type`, `account_number` |
| `epin-bundles` | `/epin/bundles` | `service_type` |
| `cable-tv-bouquets` | `/multichoice/list` | `service_type` |
| `cable-tv-addons` | `/multichoice/addons` | `service_type`, `product_code` |

Balance, category, biller, and provider-list routes always return their static catalog.

### Profile support

| Profile | Purchases | Validations | Lookups |
|---|---|---|---|
| `happy-path`, `delayed-success` | yes | yes | yes |
| `failure` | yes | yes | no |
| `pending`, `pending-then-failed`, `pending-forever` | yes | no | no |
| `pending-then-reversed` | no (no documented reversed status) | no | no |
| `timeout`, `timeout-then-failed`, `timeout-not-received` | yes | yes | yes |

A provider-wide profile is skipped for operations that cannot express it.

Examples:

```shell
# Next airtime purchase fails
curl -X POST localhost:50001/admin/overrides -H 'X-Admin-Token: simulator' -H 'Content-Type: application/json' \
  -d '{"provider":"onafriq-bill-payment","operation":"airtime","profile":"failure","count":1}'

# A specific agentReference stays pending and then succeeds
curl -X POST localhost:50001/admin/overrides -H 'X-Admin-Token: simulator' -H 'Content-Type: application/json' \
  -d '{"provider":"onafriq-bill-payment","reference":"bap-1231","profile":"pending"}'

# Every electricity purchase for meter 04042404139 fails
curl -X POST localhost:50001/admin/rules -H 'X-Admin-Token: simulator' -H 'Content-Type: application/json' \
  -d '{"provider":"onafriq-bill-payment","operation":"electricity","field":"account_number","equals":"04042404139","profile":"failure"}'
```

## Responses

All bodies use the documented response object: `status` and `code` are mandatory, `message`, `data`, and `errors` are optional. Success uses the numeric `"code": 200` as in every published example; errors use string codes from the code table.

| Situation | HTTP | `status` / `code` | Source |
|---|---|---|---|
| Purchase or requery: success | `200` | `success` / `200`, `message: "Successful"` | Response page; every purchase example |
| Purchase or requery: pending | `200` | `pending` / `EXC00114` ("The exchange is still pending.") | Response page: "200 - Ok + status: pending … use the re-query service"; code table `EXC00114` |
| Purchase or requery: failed | `500` | `error` / `BX0022` ("Failed Transaction from the service provider") | Code table `BX0022`; basic status `500 - Failure in processing transactions` and OpenAPI `500` response |
| Duplicate `agentReference` | `400` | `error` / `BX0023` ("Duplicate Agent Transaction Reference") | Code table `BX0023`; basic status `400 - Bad requests` |
| Query/requery of an unknown `agentReference` | `404` | `error` / `BX0002` ("Transaction does not exist") | Code table `BX0002` and `404 Transaction / Resource not found` |
| Name/account validation failed | `500` | `error` / `BX0025` ("Error encountered during name finder validation") | Code table `BX0025` |
| Missing credentials | `401` | `error` / `BX0013` | Code table; basic status `401` |
| Bad credentials | `401` | `error` / `SEC00001` | Code table; basic status `401` |
| Bean validation or missing query parameter | `422` | `error` / `BX0003` ("Invalid request parameters data") with `errors` | Basic status `422 - Bad requests from request field validation errors`; code table `BX0003` |
| Unreadable JSON or non-JSON content type | `400` | `error` / `BX0003` | Basic status `400`; Request page requires `application/json` |
| Simulated timeout | none | connection closed without any bytes | — |

Purchase `data` follows the published examples: `statusCode` (`"0"` on success), `transactionStatus`, `transactionReference` (a number generated by the platform), and `transactionMessage`, plus:

- `cable-tv`: `baxiReference` and `provider_message`.
- `electricity` with a `*_prepaid` `service_type`: `tokenCode` (20 digits), `tokenAmount`, `amountOfPower`.
- `epin`: `pins[]` with `instructions`, `serialNumber`, `pin`, `expiresOn`, one per `numberOfPins`.

`transactionReference`, `baxiReference`, tokens, pins, and serial numbers are derived deterministically from `agentReference`, so a requery returns exactly the original response data. A pending response carries `data.transactionStatus = "pending"` and `data.transactionReference`.

## Transaction state

Purchases are stored in the shared bounded, TTL-limited in-memory transaction store, keyed by `agentReference`, before any response delay. A client that times out can therefore requery: `timeout` and `timeout-then-failed` record the transaction (requery reports success or `BX0022`), while `timeout-not-received` records nothing (requery answers `404 BX0002`). Pending transactions move to their final status after the profile's `transition-delay`. State is lost on restart and cleared by `POST /admin/state/reset`.

## Assumptions and gaps

- **Paths.** Routes follow the exported OpenAPI. Earlier simulator versions used `/data/*`, `/cabletv/request`, `/vehicle-insurance/*`, `/superagent/balance`, `/superagent/commission-balance`, and `/superagent/transaction?reference=`, which Onafriq does not publish; they were removed. "Fetch Biller By Category" prose omits the `/services` segment; the OpenAPI server/path combination is used.
- **Pending code.** The docs state that a pending purchase is `HTTP 200 + status: pending` but do not name its `code`; `EXC00114` ("exchange is still pending") is used. `statusCode` is documented only for success (`"0"`), so pending `data` omits it.
- **Failure status.** Prose says failures use a non-200 status "(i.e. 400, 500, 503 etc.)"; the per-operation OpenAPI responses list only `500`, so purchase failures and failed requeries use `500`.
- **Requery body.** The Requery page documents the route and purpose ("re-fetching the status and response data of a previously initiated transaction") but no example; the simulator returns the original purchase response for the current status. `/superagent/transaction/query` is answered identically.
- **Unknown requery reference.** No example is published; `404` with `BX0002 Transaction does not exist` is used.
- **Validation failures.** `BX0025` is the only documented validation-failure code; it is also used for `electricity/verify` and `betting/validate`. The HTTP status for it is not documented; `500` is used.
- **Request validation errors.** `422`/`400` with `BX0003` are chosen from the basic HTTP status list and code table. The content of `errors` is not documented; the simulator lists `"<documented field> <constraint message>"`.
- **Authentication codes.** The public Onafriq staging host was observed (7 November 2026) to answer missing keys with `BX0017` and invalid keys with `BX0015`, which disagrees with the published code table (`BX0013` = token not provided, `BX0015` = invalid token, `BX0017` = server error retrieving property, `SEC00001` = bad credentials). The simulator follows the published table. Confirm the codes for the consuming organization before certification.
- **Data bundle field name.** The prose uses `service_type`; the OpenAPI schema uses `serviceType`. Both are accepted.
- **Cable TV `isBoxoffice` and `addon_code`.** The OpenAPI marks them required; the renewal prose says box office is optional and documents no add-on for renewals. They are optional.
- **E-pin count.** `numberOfPins` is limited to 1–100 to keep simulated pin lists bounded.
- **Unpublished response shapes.** `databundle/providers`, `cabletv/providers`, and `betting/billers` reuse the documented `{"providers":[{"name","service_type"}]}` shape of the e-pin providers response. `betting/validate` reuses the name-finder `data.user` shape. `jamb`, `betting`, and `vehicle-insurance` purchase responses use the common purchase `data` with `transactionMessage: "Transaction successful"` (the Response page example message). `exampin/products`, `carinsurance/products`, `billers/provider/all`, and `{biller}/account/balance` have no published response and are not simulated.
- **Catalog content.** Categories, billers, providers, bundles, bouquets, add-ons, and validation results are deterministic copies of the published examples, not a live catalog. `service_type` values are not restricted to an enum except where the OpenAPI schema fixes one (vehicle insurance `service_type`, exam `product_code`, data `package`, name-finder `action`).
- **Framework errors.** Requests to an unpublished route or with an unsupported HTTP method never reach the Onafriq module and receive the server's generic `404`/`405` rather than Onafriq's `BX0018` body.
- No claim of partner certification is made. Confirm credentials, enabled services, provider codes, amount limits, and response catalogs for the consuming organization before certification testing.
