package com.edgefort.simulator;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "simulator.providers.interswitch-transfer.config.client-id=grouped-client",
        "simulator.providers.interswitch-transfer.config.client-secret=grouped-secret",
        "simulator.providers.interswitch-transfer.config.access-token=grouped-access-token",
        "simulator.providers.interswitch-transfer.config.token-expires-in=43200",
        "simulator.providers.interswitch-transfer.config.terminal-id=GROUP001"
})
@AutoConfigureMockMvc
class InterswitchTransferApiIntegrationTest {

    private static final String CLIENT_ID = "grouped-client";
    private static final String CLIENT_SECRET = "grouped-secret";
    private static final String TERMINAL_ID = "GROUP001";

    @Autowired
    private MockMvc mockMvc;


    @Test
    void issuesAnOAuthClientCredentialsAccessToken() throws Exception {
        mockMvc.perform(post("/passport/oauth/token")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("grouped-access-token"))
                .andExpect(jsonPath("$.token_type").value("bearer"))
                .andExpect(jsonPath("$.expires_in").value(43200));

        mockMvc.perform(post("/passport/oauth/token")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials(CLIENT_ID, "wrong-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsAndExposesTheDocumentedNameEnquiryContract() throws Exception {
        mockMvc.perform(get("/quicktellerservice/api/v5/transactions/DoAccountNameInquiry")
                        .header("bankCode", "058")
                        .header("accountId", "0000000000")
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/quicktellerservice/api/v5/transactions/DoAccountNameInquiry")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("bankCode", "058")
                        .header("accountId", "0000000000")
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountName").value("SIMULATED ACCOUNT"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseDescription").value("Approved by Financial Institution"));
    }

    @Test
    void exposesTheDocumentedFundTransferBankList() throws Exception {
        mockMvc.perform(get("/quicktellerservice/api/v5/configuration/fundstransferbanks")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].BankName").value("ACCESS BANK"))
                .andExpect(jsonPath("$[0].CBNCode").value("044"))
                .andExpect(jsonPath("$[0].BankCode").value("000014"));
    }

    @Test
    void transfersAndQueriesUsingTheDocumentedV5Contract() throws Exception {
        String reference = "official-v5-transfer-001";

        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest(reference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseDescription").value("Approved by Financial Institution"))
                .andExpect(jsonPath("$.TransactionReference").value(reference));

        mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .queryParam("requestRef", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.TransactionReference").value(reference));
    }

    @Test
    void retainsSimulationScenariosBehindTheOfficialTransferContract() throws Exception {
        String reference = "official-v5-pending-001";

        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .header("X-Simulation-Scenario", "pending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest(reference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90009"))
                .andExpect(jsonPath("$.ResponseDescription").value("Request In Progress"));
    }

    @Test
    void rejectsATamperedMacAndReturnsTheDocumentedDuplicateCode() throws Exception {
        String invalidMacRequest = transferRequest("official-v5-invalid-mac")
                .replaceFirst("\"MAC\": \"[0-9a-f]+\"", "\"MAC\": \"" + "0".repeat(128) + "\"");

        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidMacRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SIMULATION_REQUEST"));

        String duplicateReference = "official-v5-duplicate-001";
        String request = transferRequest(duplicateReference);
        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        mockMvc.perform(post("/quicktellerservice/api/v5/transactions/Transfer")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90094"))
                .andExpect(jsonPath("$.ResponseDescription").value("Duplicate Transaction"));
    }

    private String bearerToken() throws Exception {
        String response = mockMvc.perform(post("/passport/oauth/token")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(response, "$.access_token");
        assertThat(accessToken).isNotBlank();
        return "Bearer " + accessToken;
    }

    private String transferRequest(String reference) throws Exception {
        String macSource = "10000566CA10000566ACNG";
        return """
                {
                  "InitiatingEntityCode": "PBL",
                  "Initiation": {
                    "Amount": 10000,
                    "CurrencyCode": "566",
                    "PaymentMethodCode": "CA",
                    "Channel": "7"
                  },
                  "Termination": {
                    "Amount": 10000,
                    "CurrencyCode": "566",
                    "PaymentMethodCode": "AC",
                    "CountryCode": "NG",
                    "AccountReceivable": {
                      "AccountNumber": "0000000000",
                      "AccountType": "00"
                    },
                    "EntityCode": "058"
                  },
                  "sender": {
                    "lastname": "Doe",
                    "othernames": "Jane",
                    "email": "jane@example.com",
                    "phone": "08000000000"
                  },
                  "Beneficiary": {
                    "lastname": "Smith",
                    "othernames": "John"
                  },
                  "TransferCode": "%s",
                  "MAC": "%s"
                }
                """.formatted(reference, sha512(macSource));
    }

    private String basicCredentials(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String sha512(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-512").digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }
}