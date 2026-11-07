package com.edgefort.simulator;

import com.edgefort.simulator.support.NoResponseAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "simulator.admin-token=local-admin",
                "simulator.providers.interswitch.config.client-id=grouped-client",
                "simulator.providers.interswitch.config.client-secret=grouped-secret",
                "simulator.providers.interswitch.config.access-token=grouped-access-token",
                "simulator.providers.interswitch.config.token-expires-in=43200",
                "simulator.providers.interswitch.config.terminal-id=3GRP0001",
                "simulator.profiles.pending.transition-delay=20ms",
                "simulator.profiles.pending-then-failed.transition-delay=20ms",
                "simulator.profiles.pending-then-reversed.transition-delay=20ms",
                "simulator.profiles.timeout.delay=50ms",
                "simulator.profiles.timeout-then-failed.delay=50ms",
                "simulator.profiles.timeout-not-received.delay=50ms"
        }
)
@AutoConfigureMockMvc
class InterswitchTransferApiIntegrationTest {

    private static final String API = "/quicktellerservice/api/v5";
    private static final String ADMIN_TOKEN = "local-admin";
    private static final String TERMINAL_ID = "3GRP0001";
    private static final String BEARER = "Bearer grouped-access-token";

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @AfterEach
    void resetSimulator() throws Exception {
        mockMvc.perform(post("/admin/state/reset").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void issuesAnOAuthClientCredentialsAccessToken() throws Exception {
        token(basicCredentials("grouped-client", "grouped-secret"), "client_credentials")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("grouped-access-token"))
                .andExpect(jsonPath("$.token_type").value("bearer"))
                .andExpect(jsonPath("$.expires_in").value(43200));

        token(basicCredentials("grouped-client", "wrong-secret"), "client_credentials")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));

        token(basicCredentials("grouped-client", "grouped-secret"), "password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));

        mockMvc.perform(post("/passport/oauth/token")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials("grouped-client", "grouped-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void rejectsMissingOrInvalidBearerTokensAndUnknownTerminals() throws Exception {
        mockMvc.perform(get(API + "/configuration/fundstransferbanks").header("TerminalId", TERMINAL_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ResponseCode").value("10003"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        mockMvc.perform(get(API + "/configuration/fundstransferbanks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ResponseCode").value("60003"));

        mockMvc.perform(get(API + "/configuration/fundstransferbanks")
                        .header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("20021"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        transfer("3PBL0001", transferRequest("140000000001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("70012"))
                .andExpect(jsonPath("$.ResponseDescription").value("Unrecognized terminal owner"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));
        query("140000000001")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("70018"));
    }

    @Test
    void answersNameEnquiryOnBothPublishedRoutesAndHonoursOperationProfiles() throws Exception {
        mockMvc.perform(get(API + "/transactions/DoAccountNameInquiry")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID)
                        .header("bankCode", "044")
                        .header("accountId", "0730804844"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AccountName").value("SIMULATED ACCOUNT"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.ResponseDescription").doesNotExist());

        mockMvc.perform(post(API + "/Transactions/DoAccountNameInquiry")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalID", TERMINAL_ID)
                        .header("bankcode", "044")
                        .header("accountid", "0730804844"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        mockMvc.perform(put("/admin/providers/interswitch/operations/name-enquiry/profile")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"failure\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(API + "/transactions/DoAccountNameInquiry")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID)
                        .header("bankCode", "044")
                        .header("accountId", "0730804844"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AccountName").doesNotExist())
                .andExpect(jsonPath("$.ResponseCode").value("40100"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        adminPut("/admin/providers/interswitch/operations/name-enquiry/profile", "{\"profile\":\"pending\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void selectsNameEnquiryBehaviorByARuleOnTheAccountIdHeader() throws Exception {
        admin("/admin/rules", """
                {"provider":"interswitch","operation":"name-enquiry","field":"accountId","equals":"0000000001","profile":"failure"}
                """).andExpect(status().isCreated());

        nameEnquiry("0000000001").andExpect(jsonPath("$.ResponseCode").value("40100"));
        nameEnquiry("0000000002").andExpect(jsonPath("$.ResponseCode").value("90000"));
    }

    @Test
    void exposesThePublishedFundTransferBankList() throws Exception {
        mockMvc.perform(get(API + "/configuration/fundstransferbanks")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banks[0].id").value("31"))
                .andExpect(jsonPath("$.banks[0].cbnCode").value("044"))
                .andExpect(jsonPath("$.banks[0].bankName").value("Access Bank Nigeria Plc"))
                .andExpect(jsonPath("$.banks[0].bankCode").value("ABP"))
                .andExpect(jsonPath("$.banks.length()").value(6));
    }

    @Test
    void transfersAndQueriesUsingThePublishedV5Contract() throws Exception {
        String request = transferRequest("140000000002");
        transfer(TERMINAL_ID, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.MAC").value(mac("100000", "100000")))
                .andExpect(jsonPath("$.TransactionDate").isString())
                .andExpect(jsonPath("$.TransactionReference").value("140000000002"))
                .andExpect(jsonPath("$.Pin").isEmpty())
                .andExpect(jsonPath("$.TransferCode").isEmpty())
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.ResponseDescription").doesNotExist());

        query("140000000002")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestReference").value("140000000002"))
                .andExpect(jsonPath("$.transactionRef").value("140000000002"))
                .andExpect(jsonPath("$.amount").value("100000"))
                .andExpect(jsonPath("$.currencyCode").value("566"))
                .andExpect(jsonPath("$.customer").value("3001155245"))
                .andExpect(jsonPath("$.customerEmail").value("jane@example.com"))
                .andExpect(jsonPath("$.customerMobile").value("08124888436"))
                .andExpect(jsonPath("$.paymentDate").isString())
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.transactionResponseCode").value("90000"))
                .andExpect(jsonPath("$.transactionSet").doesNotExist());

        transfer(TERMINAL_ID, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TransactionReference").value("140000000002"))
                .andExpect(jsonPath("$.ResponseCode").value("30020"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        mockMvc.perform(post(API + "/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest("140000000003")))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportsFailureSelectedForAReference() throws Exception {
        override("{\"provider\":\"interswitch\",\"reference\":\"140000000010\",\"profile\":\"failure\"}");

        transfer(TERMINAL_ID, transferRequest("140000000010"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90096"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));
        query("140000000010")
                .andExpect(jsonPath("$.status").value("Failed"))
                .andExpect(jsonPath("$.transactionResponseCode").value("90096"));
    }

    @Test
    void selectsTransferBehaviorByARuleOnTheBeneficiaryAccount() throws Exception {
        admin("/admin/rules", """
                {"provider":"interswitch","operation":"transfer","field":"termination.accountReceivable.accountNumber","equals":"3009999999","profile":"failure"}
                """).andExpect(status().isCreated());

        transfer(TERMINAL_ID, transferRequest("140000000011").replace("3001155245", "3009999999"))
                .andExpect(jsonPath("$.ResponseCode").value("90096"));
        transfer(TERMINAL_ID, transferRequest("140000000012"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));
    }

    @Test
    void completesPendingTransfersAsSuccessfulFailedOrReversed() throws Exception {
        override("{\"provider\":\"interswitch\",\"operation\":\"transfer\",\"profile\":\"pending\",\"count\":1}");
        transfer(TERMINAL_ID, transferRequest("140000000020"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90009"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("PENDING"));
        awaitQueryStatus("140000000020", "Completed");

        override("{\"provider\":\"interswitch\",\"reference\":\"140000000021\",\"profile\":\"pending-then-failed\"}");
        transfer(TERMINAL_ID, transferRequest("140000000021"))
                .andExpect(jsonPath("$.ResponseCode").value("90009"));
        awaitQueryStatus("140000000021", "Failed");

        override("{\"provider\":\"interswitch\",\"reference\":\"140000000022\",\"profile\":\"pending-then-reversed\"}");
        transfer(TERMINAL_ID, transferRequest("140000000022"))
                .andExpect(jsonPath("$.ResponseCode").value("90009"));
        awaitQueryStatus("140000000022", "Reversed");
        query("140000000022").andExpect(jsonPath("$.transactionResponseCode").doesNotExist());
    }

    @Test
    void pendingForeverRemainsPendingOnQuery() throws Exception {
        override("{\"provider\":\"interswitch\",\"reference\":\"140000000023\",\"profile\":\"pending-forever\"}");
        transfer(TERMINAL_ID, transferRequest("140000000023"))
                .andExpect(jsonPath("$.ResponseCode").value("90009"));
        query("140000000023")
                .andExpect(jsonPath("$.status").value("Pending"))
                .andExpect(jsonPath("$.transactionResponseCode").value("90009"));
    }

    @Test
    void timeoutClosesTheConnectionAndTheQueryReportsTheRecordedTransfer() throws Exception {
        override("{\"provider\":\"interswitch\",\"reference\":\"140000000030\",\"profile\":\"timeout\"}");
        assertNoResponse(API + "/transactions/TransferFunds", transferRequest("140000000030"));
        awaitQueryStatus("140000000030", "Completed");

        override("{\"provider\":\"interswitch\",\"reference\":\"140000000031\",\"profile\":\"timeout-then-failed\"}");
        assertNoResponse(API + "/transactions/TransferFunds", transferRequest("140000000031"));
        awaitQueryStatus("140000000031", "Failed");

        override("{\"provider\":\"interswitch\",\"reference\":\"140000000032\",\"profile\":\"timeout-not-received\"}");
        assertNoResponse(API + "/transactions/TransferFunds", transferRequest("140000000032"));
        query("140000000032")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("70018"))
                .andExpect(jsonPath("$.ResponseDescription").value("Transaction Reference not found"));
    }

    @Test
    void timeoutOnNameEnquiryClosesTheConnection() throws Exception {
        override("{\"provider\":\"interswitch\",\"operation\":\"name-enquiry\",\"profile\":\"timeout\",\"count\":1}");
        NoResponseAssertions.assertNoResponse(
                "GET",
                URI.create("http://localhost:" + port + API + "/transactions/DoAccountNameInquiry"),
                Map.of(
                        HttpHeaders.AUTHORIZATION, BEARER,
                        "TerminalId", TERMINAL_ID,
                        "bankCode", "044",
                        "accountId", "0730804844"
                ),
                null
        );
        nameEnquiry("0730804844").andExpect(jsonPath("$.ResponseCode").value("90000"));
    }

    @Test
    void rejectsTamperedMacsAndInvalidBodiesInTheInterswitchEnvelope() throws Exception {
        String invalidMac = transferRequest("140000000040")
                .replace(mac("100000", "100000"), "0".repeat(128));
        transfer(TERMINAL_ID, invalidMac)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("20050"))
                .andExpect(jsonPath("$.ResponseDescription").value("Message integrity validation failed"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        transfer(TERMINAL_ID, transferRequest("140000000041").replace("\"3001155245\"", "\"123\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"))
                .andExpect(jsonPath("$.ResponseDescription").value("Invalid Request"));

        transfer(TERMINAL_ID, "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"));

        mockMvc.perform(get(API + "/transactions/DoAccountNameInquiry")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID)
                        .header("bankCode", "044"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"));

        query("140000000040").andExpect(jsonPath("$.ResponseCode").value("70018"));
    }

    @Test
    void sharesOneQueryAndReferenceKeyspaceWithBillsPayment() throws Exception {
        transfer(TERMINAL_ID, transferRequest("140000000050"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));
        mockMvc.perform(post(API + "/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billPaymentRequest("140000000051")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        query("140000000050")
                .andExpect(jsonPath("$.customer").value("3001155245"))
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.BillPayment").doesNotExist());
        query("140000000051")
                .andExpect(jsonPath("$.serviceCode").value("628051043"))
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.transactionSet").value("BillPayment"));

        mockMvc.perform(post(API + "/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(billPaymentRequest("140000000050")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90005"));
    }

    private ResultActions token(String authorization, String grantType) throws Exception {
        return mockMvc.perform(post("/passport/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", grantType)
                .param("scope", "profile"));
    }

    private ResultActions nameEnquiry(String accountId) throws Exception {
        return mockMvc.perform(get(API + "/transactions/DoAccountNameInquiry")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .header("TerminalId", TERMINAL_ID)
                .header("bankCode", "044")
                .header("accountId", accountId));
    }

    private ResultActions transfer(String terminalId, String body) throws Exception {
        return mockMvc.perform(post(API + "/transactions/TransferFunds")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .header("TerminalId", terminalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions query(String reference) throws Exception {
        return mockMvc.perform(get(API + "/Transactions")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .header("TerminalId", TERMINAL_ID)
                .queryParam("requestRef", reference));
    }

    private void override(String json) throws Exception {
        admin("/admin/overrides", json).andExpect(status().isCreated());
    }

    private ResultActions adminPut(String path, String json) throws Exception {
        return mockMvc.perform(put(path)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions admin(String path, String json) throws Exception {
        return mockMvc.perform(post(path)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void awaitQueryStatus(String reference, String expected) {
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                query(reference).andExpect(jsonPath("$.status").value(expected))
        );
    }

    private void assertNoResponse(String path, String body) {
        NoResponseAssertions.assertNoResponse(
                "POST",
                URI.create("http://localhost:" + port + path),
                Map.of(
                        HttpHeaders.AUTHORIZATION, BEARER,
                        "TerminalId", TERMINAL_ID,
                        HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE
                ),
                body
        );
    }

    private String transferRequest(String transferCode) throws Exception {
        return """
                {
                  "transferCode": "%s",
                  "mac": "%s",
                  "termination": {
                    "amount": "100000",
                    "accountReceivable": {
                      "accountNumber": "3001155245",
                      "accountType": "00"
                    },
                    "entityCode": "044",
                    "currencyCode": "566",
                    "paymentMethodCode": "AC",
                    "countryCode": "NG"
                  },
                  "sender": {
                    "phone": "08124888436",
                    "email": "jane@example.com",
                    "lastname": "Doe",
                    "othernames": "Jane"
                  },
                  "initiatingEntityCode": "PBL",
                  "initiation": {
                    "amount": "100000",
                    "currencyCode": "566",
                    "paymentMethodCode": "CA",
                    "channel": "7"
                  },
                  "beneficiary": {
                    "lastname": "Smith",
                    "othernames": "John"
                  }
                }
                """.formatted(transferCode, mac("100000", "100000"));
    }

    private String billPaymentRequest(String reference) {
        return """
                {
                  "paymentCode": "628051043",
                  "customerId": "2348012345678",
                  "customerMobile": "2348012345678",
                  "amount": "50000",
                  "requestReference": "%s"
                }
                """.formatted(reference);
    }

    private static String mac(String initiationAmount, String terminationAmount) throws Exception {
        String secureData = initiationAmount + "566CA" + terminationAmount + "566ACNG";
        byte[] digest = MessageDigest.getInstance("SHA-512").digest(secureData.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static String basicCredentials(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
