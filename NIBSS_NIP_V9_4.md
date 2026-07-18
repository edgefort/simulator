# NIBSS NIP v9.4 implementation scope

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

Requests and responses use `application/xml`. Field names and response root names preserve the casing and ordering shown in the supplied examples. Narration is limited to 100 characters as stated in document control.

## Simulation behavior

- `SessionID` is the in-memory transaction key for direct-credit and direct-debit transfers.
- Status query returns `00` for success, `09` for processing, `96` for simulated failure, and `25` when no transaction is in memory.
- Duplicate transfers return `94`.
- A client-visible timeout returns NIP code `97` and HTTP `504` after the configured bounded delay.
- Name enquiry, balance enquiry, and advice operations support success, failure, delay, and timeout selection.
- All state is bounded, has a TTL, and is lost on restart.

`X-Simulation-Scenario` is a simulator control header and is not part of the NIP specification. It defaults to `success` and can be omitted by normal consumers.

## Deliberately not invented

The following are not implemented because they are absent or incomplete in the supplied material:

- the WSDL supplied by NIBSS during institution integration;
- SOAP action names, namespaces, service binding, and institution endpoint addresses;
- the complete message-security management rules, keys, certificates, signing, and encryption behavior;
- the remainder of amount block response 025 and subsequent message definitions;
- amount unblock, account block/unblock, financial-institution list, mandate advice, and transaction-response acknowledgement XML contracts.

The local HTTP paths are simulator transport wrappers around the documented XML messages, not a claim that these paths are NIBSS production URLs. Supply the applicable WSDL and complete security/message sections before adding a SOAP-compatible or certification-oriented transport.

## Validation and safety

Integration tests cover all implemented message pairs, required fields, malformed XML, external-entity rejection, success/failure/pending/timeout behavior, duplicates, status query, reset, provider isolation, and OpenAPI publication.