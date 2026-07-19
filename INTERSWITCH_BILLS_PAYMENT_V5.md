# Interswitch Quickteller Bills Payment v5

## Source and scope

This module was implemented from the public [Interswitch Bills Payment guide](https://docs.interswitchgroup.com/docs/bills-payment-1) and [Airtime & Data guide](https://docs.interswitchgroup.com/docs/airtime-recharge-virtual-top-up), reviewed on 18 July 2026. The airtime guide identifies itself as document version 2.0, updated 29 May 2026, for Quickteller Service API v5. The module covers the shared six-step flow:

1. retrieve categories;
2. retrieve billers in a category;
3. retrieve payment items when the biller requires them;
4. validate a customer identifier;
5. make a bill payment or airtime recharge;
6. query a transaction after completion, pending processing, or a client-visible timeout.

Airtime is not exposed as a separate provider route because the guide explicitly uses the bills-payment flow. The consumer selects category `4` (`Mobile/Recharge`), obtains a payment code, validates the phone number, and invokes `POST /Transactions`.

## Authentication and headers

The module shares the existing Quickteller OAuth simulation:

- `POST /passport/oauth/token` accepts HTTP Basic client credentials and `grant_type=client_credentials`;
- every `/quicktellerservice/api/v5/**` route requires `Authorization: Bearer <token>`;
- bills-payment routes require `TerminalId`;
- customer validation and payment also contain `TerminalId` in their bodies, and the simulator rejects a header/body mismatch;
- `X-Simulation-Scenario` is optional on payment and defaults to `success`.

Configure the shared Interswitch credentials with `INTERSWITCH_CLIENT_ID`, `INTERSWITCH_CLIENT_SECRET`, `INTERSWITCH_ACCESS_TOKEN`, `INTERSWITCH_TOKEN_EXPIRES_IN`, and `INTERSWITCH_TERMINAL_ID`.

## Implemented routes

| Method | Route | Behavior |
|---|---|---|
| `GET` | `/quicktellerservice/api/v5/services/categories` | Returns utility and mobile/recharge categories |
| `GET` | `/quicktellerservice/api/v5/services?categoryId={id}` | Returns deterministic billers for the category |
| `GET` | `/quicktellerservice/api/v5/services/options?serviceid={id}` | Returns payment items for billers that expose options |
| `POST` | `/quicktellerservice/api/v5/Transactions/validatecustomers` | Validates one or more documented customer records |
| `POST` | `/quicktellerservice/api/v5/Transactions` | Purchases a bill or airtime product |
| `GET` | `/quicktellerservice/api/v5/Transactions?requestRef={reference}` | Queries bills-payment or Send Money transactions by reference |

The deterministic catalog intentionally stays small and lightweight:

| Service | Category | Biller ID | Payment code | Amount rule | Result |
|---|---:|---:|---|---|---|
| Abuja Disco Buy Power Prepaid | `1` | `17589` | `051758901` | At least `35000` kobo | Pin-bearing response |
| MTN e-Charge Prepaid | `4` | `109` | `628051043` | `5000`–`1000000` kobo | Network recharge metadata |
| 9mobile/Etisalat Recharge Top-Up | `4` | `120` | `6280510490` | `5000`–`1000000` kobo | Network recharge metadata |
| Glo QuickCharge | `4` | `402` | `628051045` | `5000`–`1000000` kobo | Network recharge metadata |

These values come from examples in the public guide. The simulator does not copy the guide's large and changing production catalog; consumers should discover and use the deterministic entries returned by this instance.

## Payment example

```json
{
  "TerminalId": "3PBL0001",
  "paymentCode": "628051043",
  "customerId": "2348012345678",
  "customerMobile": "2348012345678",
  "customerEmail": "customer@example.com",
  "amount": "50000",
  "requestReference": "airtime-local-001"
}
```

Amounts are in kobo. Regular airtime accepts ₦50–₦10,000 (`5000`–`1000000` kobo). `requestReference` is required, unique, and limited to 20 characters. Successful airtime responses include `network`, `phoneNumber`, and naira-denominated `amountCredited` in `AdditionalInfo`. The simulator returns documented common codes including:

- `90000` for success;
- `90003` for an invalid amount;
- `90005` for a duplicate transaction;
- `90006` for a simulated provider failure;
- `90007` for an invalid payment code;
- `90008` when customer validation contains an invalid product.

`90009` and `SIMULATOR_TIMEOUT` are simulator lifecycle extensions used consistently with the existing Interswitch module for pending and client-visible timeout behavior.

## Simulation behavior and state

The payment operation supports `success`, `failure`, `pending`, `delayed-success`, `timeout`, `event-timeout`, and `late-event`. One-time admin overrides take precedence over the request header. The admin provider ID is `interswitch-bill-payment`.

Transactions and the minimum query metadata are held in bounded TTL memory only. State resets on restart, no database or broker is used, and duplicate checks are atomic through the shared transaction store. Pending and late-event transactions transition through the shared scheduler; event-timeout transactions remain pending until state expires or is reset.

## Public-documentation limitations

- This is an implementation of the reviewed public guide, not a claim of Interswitch certification or production catalog parity.
- The guide's examples are internally inconsistent for the Abuja Disco product: the payment item reports `AmountType` `2` and amount `0`, while customer validation describes a minimum of `35000` kobo. The simulator follows the validation rule and accepts amounts at or above `35000`.
- The airtime guide lists MTN with `AmountType` `2` while separately defining regular airtime as an any-amount product from ₦50 through ₦10,000. The simulator applies the stated regular-airtime range and reports `AmountType` `0` for its deterministic recharge products.
- The public airtime catalog example shows an Airtel data-bundle product rather than a regular Airtel recharge payment code. Data bundles and invented Airtel airtime identifiers are outside this airtime-recharge implementation.
- Real billers, products, fees, customer records, balances, and token values are not fetched from Interswitch. Responses are deterministic simulation data.
- The public guide documents statuses `Completed`, `Pending`, `Failed`, and `Reversed`; this module produces the first three. No reversal request operation is published in the reviewed guide, so the simulator does not invent one.
- Partner-specific network controls, certificates, credential provisioning, reconciliation, and settlement behavior are outside the public contract and are not simulated.