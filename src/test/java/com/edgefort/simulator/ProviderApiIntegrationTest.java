package com.edgefort.simulator;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest(properties = {
        "simulator.admin-token=local-admin",
        "simulator.profiles.pending.transition-delay=20ms",
        "simulator.profiles.late-event.transition-delay=80ms",
        "simulator.profiles.timeout.delay=5ms"
})
@AutoConfigureMockMvc
class ProviderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    void nibssTransferIsStoredRequeriedAndDuplicateIsRejected() throws Exception {
        String sessionId = "nip-reference-100";
        String body = nibssTransfer(sessionId);

        mockMvc.perform(post("/nip/v9.4/fundtransfersingleitem_dc")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("00"));

        mockMvc.perform(post("/nip/v9.4/txnstatusquerysingleitem")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(nibssStatusQuery(sessionId)))
                .andExpect(status().isOk())
                .andExpect(xpath("/TSQuerySingleResponse/SessionID").string(sessionId))
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("00"));

        mockMvc.perform(post("/nip/v9.4/fundtransfersingleitem_dc")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("94"));
    }

    @Test
    void providersUseIndependentContractsAndState() throws Exception {
        String sharedReference = "shared-provider-reference";
        mockMvc.perform(post("/nip/v9.4/fundtransfersingleitem_dc")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(nibssTransfer(sharedReference)))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("00"));

        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                        .header("TerminalId", "3PBL0001")
                        .header("X-Simulation-Scenario", "failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interswitchTransfer(sharedReference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90096"))
                .andExpect(jsonPath("$.responseCode").doesNotExist());

        mockMvc.perform(post("/nip/v9.4/txnstatusquerysingleitem")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(nibssStatusQuery(sharedReference)))
                .andExpect(status().isOk())
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("00"));
        mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                        .header("TerminalId", "3PBL0001")
                        .queryParam("requestRef", sharedReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90096"));
    }

    @Test
    void validatesRequestsAndSimulatesClientVisibleTimeout() throws Exception {
        mockMvc.perform(post("/nip/v9.4/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<NESingleRequest/>"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                        .header("TerminalId", "3PBL0001")
                        .header("X-Simulation-Scenario", "timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interswitchTransfer("timeout-reference")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.ResponseCode").value("SIMULATOR_TIMEOUT"));
    }

    @Test
    void nameEnquiryAndBeneficiaryValidationKeepProviderContractsIndependent() throws Exception {
        mockMvc.perform(post("/nip/v9.4/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(nibssNameEnquiry("name-enquiry-reference")))
                .andExpect(status().isOk())
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("00"))
                .andExpect(xpath("/NESingleResponse/AccountName").string("SIMULATED ACCOUNT"));

        mockMvc.perform(get("/quicktellerservice/api/v5/transactions/DoAccountNameInquiry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                        .header("TerminalId", "3PBL0001")
                        .header("bankCode", "058")
                        .header("accountId", "0123456789")
                        .header("X-Simulation-Scenario", "failure")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90012"))
                .andExpect(jsonPath("$.accountName").doesNotExist());
    }

    @Test
    void missingScenarioHeaderDefaultsToSuccessRegardlessOfActiveProfile() throws Exception {
        mockMvc.perform(put("/admin/profiles/failure/activate")
                        .header("X-Admin-Token", "local-admin"))
                .andExpect(status().isNoContent());

        try {
            mockMvc.perform(post("/nip/v9.4/nameenquirysingle")
                            .contentType(MediaType.APPLICATION_XML)
                            .content(nibssNameEnquiry("default-success-reference")))
                    .andExpect(status().isOk())
                    .andExpect(xpath("/NESingleResponse/ResponseCode").string("00"))
                    .andExpect(xpath("/NESingleResponse/AccountName").string("SIMULATED ACCOUNT"));
        } finally {
            mockMvc.perform(put("/admin/profiles/happy-path/activate")
                            .header("X-Admin-Token", "local-admin"))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void adminOverrideIsProtectedAndConsumedOnce() throws Exception {
        mockMvc.perform(post("/admin/overrides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"nibss-nip","reference":"override-reference","scenario":"FAILURE"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/overrides")
                        .header("X-Admin-Token", "local-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"nibss-nip","reference":"override-reference","scenario":"FAILURE"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/nip/v9.4/fundtransfersingleitem_dc")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(nibssTransfer("override-reference")))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("96"));

        mockMvc.perform(delete("/admin/state")
                        .header("X-Admin-Token", "local-admin"))
                .andExpect(status().isNoContent());
    }

    @Test
    void exposesProviderMetadataWithSpecificationVersions() throws Exception {
        mockMvc.perform(get("/admin/providers")
                        .header("X-Admin-Token", "local-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.id == 'nibss-nip')].specificationVersion").value("NIP v9.4"))
                .andExpect(jsonPath("$[?(@.id == 'interswitch-transfer')].specificationVersion")
                        .value("Quickteller Service API v5 public documentation"))
                .andExpect(jsonPath("$[?(@.id == 'interswitch-bill-payment')].specificationVersion")
                        .value("Quickteller Bills Payment and Airtime API v5 public documentation"))
                .andExpect(jsonPath("$[?(@.id == 'onafriq-bill-payment')].specificationVersion")
                        .value("Biller Aggregation Platform API v1.0.0 public documentation"));
    }

    @Test
    void pendingNipTransferCompletesInMemory() throws Exception {
        String sessionId = "pending-nip-reference";
        mockMvc.perform(post("/nip/v9.4/fundtransfersingleitem_dc")
                        .header("X-Simulation-Scenario", "pending")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(nibssTransfer(sessionId)))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("09"));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                mockMvc.perform(post("/nip/v9.4/txnstatusquerysingleitem")
                                .contentType(MediaType.APPLICATION_XML)
                                .content(nibssStatusQuery(sessionId)))
                        .andExpect(status().isOk())
                        .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("00"))
        );
    }

    @Test
    void eventTimeoutSuppressesCallbackAndLeavesTransferPending() throws Exception {
        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                        .header("TerminalId", "3PBL0001")
                        .header("X-Simulation-Scenario", "event-timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interswitchTransfer("suppressed-event-reference")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90009"));

        Awaitility.await().pollDelay(Duration.ofMillis(120)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                                .header("TerminalId", "3PBL0001")
                                .queryParam("requestRef", "suppressed-event-reference"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.ResponseCode").value("90009"))
        );
        mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                        .header("TerminalId", "3PBL0001")
                        .queryParam("requestRef", "suppressed-event-reference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90009"));
    }

    @Test
    void lateEventCompletesAfterTheConfiguredDeadline() throws Exception {
        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                        .header("TerminalId", "3PBL0001")
                        .header("X-Simulation-Scenario", "late-event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interswitchTransfer("late-event-reference")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90009"));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                                .header("TerminalId", "3PBL0001")
                                .queryParam("requestRef", "late-event-reference"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.ResponseCode").value("90000"))
        );
        mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                        .header("TerminalId", "3PBL0001")
                        .queryParam("requestRef", "late-event-reference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"));
    }

    @Test
    void adminResetCancelsQueuedNipCompletionAndClearsState() throws Exception {
        String sessionId = "reset-pending-reference";
        mockMvc.perform(post("/nip/v9.4/fundtransfersingleitem_dc")
                        .header("X-Simulation-Scenario", "late-event")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(nibssTransfer(sessionId)))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("09"));

        mockMvc.perform(delete("/admin/state")
                        .header("X-Admin-Token", "local-admin"))
                .andExpect(status().isNoContent());

        Awaitility.await().pollDelay(Duration.ofMillis(150)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                mockMvc.perform(post("/nip/v9.4/txnstatusquerysingleitem")
                                .contentType(MediaType.APPLICATION_XML)
                                .content(nibssStatusQuery(sessionId)))
                        .andExpect(status().isOk())
                        .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("25"))
        );
    }

    private String nibssNameEnquiry(String sessionId) {
        return """
                <NESingleRequest>
                  <SessionID>%s</SessionID>
                  <DestinationInstitutionCode>999999</DestinationInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <AccountNumber>0123456789</AccountNumber>
                </NESingleRequest>
                """.formatted(sessionId);
    }

    private String nibssTransfer(String sessionId) {
        return """
                <FTSingleCreditRequest>
                  <SessionID>%s</SessionID>
                  <NameEnquiryRef>name-enquiry-reference</NameEnquiryRef>
                  <DestinationInstitutionCode>999999</DestinationInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <BeneficiaryAccountName>SIMULATED BENEFICIARY</BeneficiaryAccountName>
                  <BeneficiaryAccountNumber>0123456789</BeneficiaryAccountNumber>
                  <BeneficiaryBankVerificationNumber>00000000000</BeneficiaryBankVerificationNumber>
                  <BeneficiaryKYCLevel>1</BeneficiaryKYCLevel>
                  <OriginatorAccountName>SIMULATED ORIGINATOR</OriginatorAccountName>
                  <OriginatorAccountNumber>9876543210</OriginatorAccountNumber>
                  <OriginatorBankVerificationNumber>11111111111</OriginatorBankVerificationNumber>
                  <OriginatorKYCLevel>1</OriginatorKYCLevel>
                  <TransactionLocation>6.4300747,3.4110715</TransactionLocation>
                  <Narration>Simulation transfer</Narration>
                  <PaymentReference>payment-reference</PaymentReference>
                  <Amount>500.00</Amount>
                </FTSingleCreditRequest>
                """.formatted(sessionId);
    }

    private String nibssStatusQuery(String sessionId) {
        return """
                <TSQuerySingleRequest>
                  <SourceInstitutionCode>999999</SourceInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <SessionID>%s</SessionID>
                </TSQuerySingleRequest>
                """.formatted(sessionId);
    }

    private String interswitchTransfer(String reference) throws Exception {
        String secureData = "500566CA500566ACNG";
        String mac = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-512").digest(secureData.getBytes(StandardCharsets.UTF_8))
        );
        return """
                {
                  "InitiatingEntityCode": "PBL",
                  "Initiation": {
                    "Amount": 500,
                    "CurrencyCode": "566",
                    "PaymentMethodCode": "CA",
                    "Channel": "7"
                  },
                  "Termination": {
                    "Amount": 500,
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
                  "TransferCode": "%s",
                  "MAC": "%s"
                }
                """.formatted(reference, mac);
    }
}