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
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "simulator.providers.interswitch-transfer.config.client-id=bills-client",
        "simulator.providers.interswitch-transfer.config.client-secret=bills-secret",
        "simulator.providers.interswitch-transfer.config.access-token=bills-access-token",
        "simulator.providers.interswitch-transfer.config.terminal-id=BILLS001",
        "simulator.profiles.pending.transition-delay=20ms",
        "simulator.profiles.timeout.delay=5ms"
})
@AutoConfigureMockMvc
class InterswitchBillPaymentApiIntegrationTest {

    private static final String TERMINAL_ID = "BILLS001";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectsAndExposesTheDocumentedCategoryAndBillerCatalogs() throws Exception {
        mockMvc.perform(get("/quicktellerservice/api/v5/services/categories")
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/quicktellerservice/api/v5/services/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BillerCategories[?(@.Id == 1)].Name").value("Utility Bills"))
                .andExpect(jsonPath("$.BillerCategories[?(@.Id == 4)].Name").value("Mobile/Recharge"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("SUCCESSFUL"));

        mockMvc.perform(get("/quicktellerservice/api/v5/services")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .queryParam("categoryId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BillerList.Count").value(3))
                .andExpect(jsonPath("$.BillerList.Category[0].Id").value(4))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[?(@.Id == 109)].ProductCode")
                        .value("628051043"))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[?(@.Id == 120)].ProductCode")
                        .value("6280510490"))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[?(@.Id == 402)].ProductCode")
                        .value("628051045"))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[0].CurrencyCode").value("566"))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[0].CategoryId").value(4))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[0].Type").value("MO"));
    }

    @Test
    void exposesDocumentedPaymentItemsAndValidatesCustomers() throws Exception {
        mockMvc.perform(get("/quicktellerservice/api/v5/services/options")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .queryParam("serviceid", "17589"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PaymentItems[0].PaymentCode").value("051758901"))
                .andExpect(jsonPath("$.PaymentItems[0].BillerId").value("17589"))
                .andExpect(jsonPath("$.PaymentItems[0].IsAmountFixed").value(true))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions/validatecustomers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequest("051758901", "12345678910")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Customers[0].TerminalId").value(TERMINAL_ID))
                .andExpect(jsonPath("$.Customers[0].PaymentCode").value("051758901"))
                .andExpect(jsonPath("$.Customers[0].CustomerId").value("12345678910"))
                .andExpect(jsonPath("$.Customers[0].FullName").value("SIMULATED CUSTOMER"))
                .andExpect(jsonPath("$.Customers[0].ResponseCode").value("90000"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions/validatecustomers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequest("unknown", "12345678910")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Customers[0].ResponseCode").value("90007"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));
    }

    @Test
    void purchasesBillsAndAirtimeAndQueriesTheirDocumentedStatus() throws Exception {
        String billReference = "bill-20260718-001";
        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("051758901", "12345678910", "100000", billReference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TransactionRef").isString())
                .andExpect(jsonPath("$.RechargePIN").isString())
                .andExpect(jsonPath("$.ApprovedAmount").value("100000"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("SUCCESSFUL"));

        mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .queryParam("requestRef", billReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestReference").value(billReference))
                .andExpect(jsonPath("$.transactionRef").isString())
                .andExpect(jsonPath("$.serviceCode").value("051758901"))
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.transactionResponseCode").value("90000"))
                .andExpect(jsonPath("$.transactionSet").value("BillPayment"));

        String airtimeReference = "airtime-20260718-01";
        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051043", "2348012345678", "50000", airtimeReference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ApprovedAmount").value("50000"))
                .andExpect(jsonPath("$.RechargePIN").doesNotExist())
                .andExpect(jsonPath("$.AdditionalInfo.network").value("MTN"))
                .andExpect(jsonPath("$.AdditionalInfo.phoneNumber").value("2348012345678"))
                .andExpect(jsonPath("$.AdditionalInfo.amountCredited").value("500.00"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));
    }

    @Test
    void preservesDuplicatePendingTimeoutAndValidationBehavior() throws Exception {
        String duplicateReference = "bill-duplicate-001";
        String duplicateRequest = paymentRequest("628051043", "2348012345678", "50000", duplicateReference);
        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90005"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .header("X-Simulation-Scenario", "pending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051043", "2348012345678", "50000", "bill-pending-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90009"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("PENDING"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .header("X-Simulation-Scenario", "timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051043", "2348012345678", "50000", "bill-timeout-001")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.ResponseCode").value("SIMULATOR_TIMEOUT"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051043", "2348012345678", "0", "reference-is-too-long-001")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMismatchedTerminalsAndReturnsDocumentedProductErrors() throws Exception {
        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions/validatecustomers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", "DIFFERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequest("051758901", "12345678910")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SIMULATION_REQUEST"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("unknown", "12345678910", "50000", "unknown-code-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90007"))
                .andExpect(jsonPath("$.ResponseDescription").value("Invalid payment code"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("051758901", "12345678910", "34999", "below-minimum-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90003"))
                .andExpect(jsonPath("$.ResponseDescription").value("Invalid amount"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", "DIFFERENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051043", "2348012345678", "50000", "terminal-mismatch-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SIMULATION_REQUEST"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051043", "2348012345678", "4999", "airtime-too-low-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90003"));

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051043", "2348012345678", "1000001", "airtime-too-high-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90003"));

        mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .queryParam("requestRef", "missing-reference"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearsQueryStateAndReplacesMetadataWhenAReferenceIsReusedAfterReset() throws Exception {
        String reference = "airtime-reset-001";
        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051043", "2348012345678", "50000", reference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        mockMvc.perform(delete("/admin/state")
                        .header("X-Admin-Token", "simulator"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .queryParam("requestRef", reference))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("628051045", "2348012345678", "50000", reference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AdditionalInfo.network").value("GLO"));

        mockMvc.perform(get("/quicktellerservice/api/v5/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .header("TerminalId", TERMINAL_ID)
                        .queryParam("requestRef", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceCode").value("628051045"));
    }

    private String bearerToken() throws Exception {
        String response = mockMvc.perform(post("/passport/oauth/token")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials())
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

    private String basicCredentials() {
        String credentials = "bills-client:bills-secret";
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String validationRequest(String paymentCode, String customerId) {
        return """
                {
                  "customers": [{
                    "PaymentCode": "%s",
                    "CustomerId": "%s",
                    "WithDetails": true
                  }],
                  "TerminalId": "%s"
                }
                """.formatted(paymentCode, customerId, TERMINAL_ID);
    }

    private String paymentRequest(String paymentCode, String customerId, String amount, String reference) {
        return """
                {
                  "TerminalId": "%s",
                  "paymentCode": "%s",
                  "customerId": "%s",
                  "customerMobile": "2348012345678",
                  "customerEmail": "customer@example.com",
                  "amount": "%s",
                  "requestReference": "%s"
                }
                """.formatted(TERMINAL_ID, paymentCode, customerId, amount, reference);
    }
}