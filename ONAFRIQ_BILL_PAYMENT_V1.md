# Onafriq Biller Aggregation Platform API v1.0.0

## Source and scope

This module was implemented from the public Onafriq B2B VAS project at <https://developers.onafriq.com/docs/b2b-vas/52r1qylqo22f2-overview>, reviewed on 18 July 2026. The exported OpenAPI document identifies the service as `Onafriq Biller Aggregation Platform`, OpenAPI `3.0.0`, version `1.0.0`, with staging and production bases ending in `/services`.

The documentation project contains 41 operations across VAS, fund transfer, and international airtime/remittance products. This module intentionally implements the bill-payment subset only. It does not expose the fund-transfer or international/remittance routes as bill-payment operations.

When the prose and exported OpenAPI conflict, the exported OpenAPI path and request schema take precedence. Examples are used for response envelopes because most OpenAPI response entries declare status codes without response-body schemas.

## Authentication

Every `/services/**` operation requires either:

- `x-api-key: <key>`; or
- `Authorization: Api-key <key>`.

The default local value is `simulator-api-key`. Replace it with `ONAFRIQ_API_KEY`.

The public authentication guide also discusses a Baxi HMAC mode. That signing mode is not implemented because an authoritative, machine-readable canonical-string contract was not available in the exported operation specification. The simulator does not invent signing behavior.

## Implemented operations

| Capability | Operations |
|---|---|
| Balances | `GET /services/superagent/balance`, `GET /services/superagent/commission-balance` |
| Transaction state | `GET /services/superagent/transaction?reference=…`, `GET /services/superagent/transaction/requery?agentReference=…` |
| Biller discovery | `GET /services/billers/providers/all`, `GET /services/billers/category/all`, `GET /services/billers/services/category/{category}`, `GET /services/billers/services/provider/{provider}` |
| Airtime | `GET /services/airtime/providers`, `POST /services/airtime/request` |
| Data | `GET /services/data/providers`, `POST /services/data/bundles`, `POST /services/data/request` |
| Cable TV | `GET /services/cabletv/providers`, `POST /services/cabletv/check-addons`, `POST /services/cabletv/request` |
| Electricity | `GET /services/electricity/providers`, `POST /services/electricity/verify`, `POST /services/electricity/request` |
| E-pin/JAMB | `GET /services/epin/providers`, `POST /services/epin/bundles`, `POST /services/epin/request`, `GET /services/epin/jamb-profiles`, `POST /services/epin/jamb-request` |
| Betting | `GET /services/betting/providers`, `POST /services/betting/verify`, `POST /services/betting/request` |
| Account finder | `POST /services/namefinder/query` |
| Vehicle insurance | `GET /services/vehicle-insurance/providers`, `POST /services/vehicle-insurance/request` |

All request field names retain the documented casing, including snake-case fields such as `service_type`, `account_number`, `data_code`, `total_amount`, and `smartcard_number`, and camel-case fields such as `agentId` and `agentReference`.

## Response and state behavior

Responses use the documented common envelope:

```json
{
  "status": "success",
  "code": 200,
  "message": "Successful",
  "data": {},
  "errors": []
}
```

Purchase state is keyed by `agentReference` in the shared bounded TTL in-memory transaction store. It is disposable on restart and cleared by the Admin API. No database, broker, cache, or provider call is used.

| Simulator outcome | Onafriq behavior |
|---|---|
| Success | HTTP `200`, `status=success`, `code=200` |
| Failure | HTTP `503`, `status=error`, `code=BX0022` |
| Pending/event timeout/late event | HTTP `200`, `status=pending`, `code=EXC00114`; requery reflects in-memory completion when scheduled |
| Timeout | HTTP `504`, `status=error`, `code=BX0001` |
| Duplicate `agentReference` | HTTP `400`, `status=error`, `code=BX0023` |
| Unknown requery reference | HTTP `404`, `status=error`, `code=BX0018` |
| Missing API key | HTTP `401`, `status=error`, `code=BX0013` |
| Invalid API key | HTTP `401`, `status=error`, `code=SEC00001` |

Use `X-Simulation-Scenario` on purchase operations. The supported values are `success`, `failure`, `pending`, `delayed-success`, `timeout`, `event-timeout`, and `late-event`. The default is `success`.

## Known public-documentation limitations

- Most exported OpenAPI responses have status descriptions but no JSON schemas, so response bodies follow published examples and the common response guide.
- Some prose examples omit the `/services` base segment; the exported server/path combination is authoritative.
- The OpenAPI project contains both Baxi-era field/code terminology and Onafriq branding.
- Discovery data in the simulator is deterministic sample data, not a live copy of Onafriq’s current product catalog.
- No claim of partner certification is made. Confirm credentials, enabled services, provider codes, amount limits, response catalogs, and any HMAC profile assigned to the consuming organization before certification testing.