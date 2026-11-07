# NIBSS NIP v9.4 implementation scope

Provider id: `nibss-nip`.

## Source

This module is based only on the NIBSS Instant Payments technical specification text supplied for this project:

- version: 9.4;
- document date: 16 June 2017;
- complete XML examples available in the supplied text: messages 010 through 023;
- the supplied text ends partway through message 025.

No third-party NIP examples were used to fill missing contract details.

## Implemented XML message pairs

| Messages | XML roots | Local route |
|---|---|---|
| 010/011 | `NESingleRequest` / `NESingleResponse` | `POST /nip/v9.4/nameenquirysingle` |
| 012/013 | `FTSingleCreditRequest` / `FTSingleCreditResponse` | `POST /nip/v9.4/fundtransfersingleitem_dc` |
| 014/015 | `FTSingleDebitRequest` / `FTSingleDebitResponse` | `POST /nip/v9.4/fundtransfersingleitem_dd` |
| 016/017 | `TSQuerySingleRequest` / `TSQuerySingleResponse` | `POST /nip/v9.4/txnstatusquerysingleitem` |
| 018/019 | `BalanceEnquiryRequest` / `BalanceEnquiryResponse` | `POST /nip/v9.4/balanceenquiry` |
| 020/021 | `FTAdviceCreditRequest` / `FTAdviceCreditResponse` | `POST /nip/v9.4/fundtransferAdvice_dc` |
| 022/023 | `FTAdviceDebitRequest` / `FTAdviceDebitResponse` | `POST /nip/v9.4/fundtransferAdvice_dd` |

Requests must be `application/xml`; every response is `application/xml` with HTTP `200`. Field names and response root names preserve the casing and ordering shown in the supplied examples. Narration is limited to 100 characters as stated in document control. Requests carry nothing but the NIP message: no simulator header, parameter, or element exists.

When `simulator.providers.nibss-nip.base-path` is set (for example `/nibss`), every route above is served under that prefix (`/nibss/nip/v9.4/...`) and nowhere else.

## Transport placeholder

The HTTP routes are a placeholder transport for the documented XML messages until the WSDL and message-security specification are supplied. They are not NIBSS production URLs, and the SOAP binding (service, SOAP actions, namespaces, envelope) and message security (keys, certificates, signing, encryption) are pending. Because no security scheme was supplied, the routes require no authentication. Once the WSDL and security specification are provided, the transport will be replaced by the SOAP binding; the message contents and simulated behavior described here stay the same.

## Response codes

| Code | Meaning | Used for |
|---|---|---|
| `00` | Approved or completed successfully | Successful operation; status query of a successful transfer |
| `09` | Request processing in progress | Pending transfer; status query of a pending transfer |
| `25` | Unable to locate record | Status query for a `SessionID` with no transaction |
| `30` | Format error | Missing or invalid elements, unreadable XML, non-XML content type (see [Error rendering](#error-rendering)) |
| `94` | Duplicate transaction | Transfer repeating the `SessionID` of an existing transfer |
| `96` | System malfunction | Failed operation; status query of a failed transfer |

NIP has no response code for a client-side timeout: a simulated timeout returns no response at all. NIP has no status-query code for a reversed transaction, so reversal cannot be simulated for this provider.

## Simulated operations

Behavior is selected only through the admin API or configuration (`/admin/overrides`, `/admin/rules`, `/admin/providers/nibss-nip/...`); the reference used by overrides is the NIP `SessionID`. Test-data rules may match the listed request elements, named exactly as in the NIP messages.

| Operation | Message | Kind | Outcomes | Status query reports | Match fields |
|---|---|---|---|---|---|
| `name-enquiry` | 010/011 | stateless | success, failure, timeout | — | `DestinationInstitutionCode`, `ChannelCode`, `AccountNumber` |
| `fund-transfer-direct-credit` | 012/013 | transactional | success, failure, pending, timeout | pending, success, failed | `DestinationInstitutionCode`, `ChannelCode`, `BeneficiaryAccountNumber`, `OriginatorAccountNumber`, `Amount` |
| `fund-transfer-direct-debit` | 014/015 | transactional | success, failure, pending, timeout | pending, success, failed | `DestinationInstitutionCode`, `ChannelCode`, `DebitAccountNumber`, `BeneficiaryAccountNumber`, `MandateReferenceNumber`, `Amount` |
| `balance-enquiry` | 018/019 | stateless | success, failure, timeout | — | `DestinationInstitutionCode`, `ChannelCode`, `TargetAccountNumber` |
| `fund-transfer-advice-direct-credit` | 020/021 | stateless | success, failure, timeout | — | same as `fund-transfer-direct-credit` |
| `fund-transfer-advice-direct-debit` | 022/023 | stateless | success, failure, timeout | — | same as `fund-transfer-direct-debit` |

`Amount` is matched as its plain decimal text, for example `1000.00`.

The transaction status query (016/017) is not a simulated operation: it reports the recorded state of a direct-credit or direct-debit transfer by `SessionID` and cannot be assigned a profile.

### Supported profiles

| Profile | Stateless operations | Transfers |
|---|---|---|
| `happy-path`, `delayed-success` | `00` | `00`; query `00` |
| `failure` | `96` | `96`; query `96` |
| `pending` | not supported | `09`; query `09`, then `00` |
| `pending-then-failed` | not supported | `09`; query `09`, then `96` |
| `pending-forever` | not supported | `09`; query `09` until the record expires |
| `pending-then-reversed` | not supported | not supported (no reversed code) |
| `timeout` | no response | no response; query `00` |
| `timeout-then-failed` | no response | no response; query `96` |
| `timeout-not-received` | no response | no response; query `25` |

The admin API rejects a profile an operation cannot express. A timeout holds the request for the profile delay and then closes the connection without sending any bytes.

## Error rendering

Requests that cannot be processed are answered with the operation's own response message (for example `NESingleResponse` for name enquiry) carrying `ResponseCode` `30`, HTTP `200`, and `application/xml`:

- missing or invalid required elements: request elements that were read are echoed as in a normal response;
- unreadable XML, including any document with a DTD or external entity (rejected by the XML reader; entities are never resolved): only `ResponseCode` is populated;
- a request that is not `application/xml`: only `ResponseCode` is populated.

The transaction is not recorded in any of these cases.

## Limitations and assumptions

- The response-code meanings above follow the NIP response-code list; confirm them against the full v9.4 document when it is supplied, since the supplied excerpt ends partway through message 025.
- The supplied text does not define how NIBSS answers a malformed message, a wrong content type, or a missing element over this transport. The simulator uses the closest documented form: the operation's own response message with `30` (format error) and HTTP `200`. The SOAP binding may define SOAP faults for some of these cases; this will be revisited with the WSDL.
- Every failure is reported as `96`. More specific NIP failure codes (for example invalid account or insufficient funds) are not simulated.
- Requests for undefined routes or unsupported HTTP methods are rejected by the simulator before any NIBSS handler is selected and are not answered in NIP form.
- Not implemented because absent or incomplete in the supplied material: the WSDL, SOAP action names, namespaces, service binding, and endpoint addresses; message-security rules, keys, certificates, signing, and encryption; the remainder of amount block response 025 and subsequent messages; amount unblock, account block/unblock, financial-institution list, mandate advice, and transaction-response acknowledgement.
- All state is in memory, bounded, has a TTL, and is lost on restart.

## Validation and safety

Integration tests cover all implemented message pairs, success, failure, pending completion as success and failed, timeout as a dropped connection followed by the recorded status, timeout not received (`25`), duplicates, test-data rules on NIP elements, profiles the contract cannot express, format errors for missing elements, malformed XML, non-XML content types and external entities, and base-path mounting.
