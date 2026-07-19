package com.edgefort.simulator;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "simulator.providers.onafriq-bill-payment.config.api-key=onafriq-test-key",
        "simulator.profiles.pending.transition-delay=20ms",
        "simulator.profiles.timeout.delay=5ms"
})
@AutoConfigureMockMvc
class OnafriqBillPaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresDocumentedApiKeyAuthentication() throws Exception {
        mockMvc.perform(get("/services/airtime/providers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0013"));

        mockMvc.perform(get("/services/airtime/providers")
                        .header("x-api-key", "onafriq-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/services/airtime/providers")
                        .header(HttpHeaders.AUTHORIZATION, "Api-key onafriq-test-key"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/services/airtime/providers")
                        .header("x-api-key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SEC00001"));
    }

    @Test
    void exposesDocumentedDiscoveryAndValidationOperations() throws Exception {
        mockMvc.perform(get("/services/billers/category/all")
                        .header("x-api-key", "onafriq-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.service_type == 'electricity')]").exists());

        mockMvc.perform(post("/services/electricity/verify")
                        .header("x-api-key", "onafriq-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "service_type": "eko_electric_postpaid",
                                  "account_number": "6528651914"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accountNumber").value("6528651914"));

        mockMvc.perform(post("/services/namefinder/query")
                        .header("x-api-key", "onafriq-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "service_type": "spectranet",
                                  "account_number": "210001380"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNumber").value("210001380"));
    }

    @Test
    void purchaseCanBeRequeriedAndDuplicateReferenceIsRejected() throws Exception {
        String body = airtimeRequest("onafriq-airtime-100");

        mockMvc.perform(post("/services/airtime/request")
                        .header("x-api-key", "onafriq-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.transactionStatus").value("success"))
                .andExpect(jsonPath("$.data.agentReference").value("onafriq-airtime-100"));

        mockMvc.perform(get("/services/superagent/transaction/requery")
                        .header("x-api-key", "onafriq-test-key")
                        .queryParam("agentReference", "onafriq-airtime-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionStatus").value("success"))
                .andExpect(jsonPath("$.data.agentReference").value("onafriq-airtime-100"));

        mockMvc.perform(post("/services/airtime/request")
                        .header("x-api-key", "onafriq-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0023"));
    }

    @Test
    void supportsPendingCompletionFailureTimeoutAndValidation() throws Exception {
        mockMvc.perform(post("/services/electricity/request")
                        .header("x-api-key", "onafriq-test-key")
                        .header("X-Simulation-Scenario", "pending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(electricityRequest("onafriq-pending-100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.code").value("EXC00114"));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                mockMvc.perform(get("/services/superagent/transaction/requery")
                                .header("x-api-key", "onafriq-test-key")
                                .queryParam("agentReference", "onafriq-pending-100"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("success"))
        );

        mockMvc.perform(post("/services/airtime/request")
                        .header("x-api-key", "onafriq-test-key")
                        .header("X-Simulation-Scenario", "failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(airtimeRequest("onafriq-failure-100")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0022"));

        mockMvc.perform(post("/services/airtime/request")
                        .header("x-api-key", "onafriq-test-key")
                        .header("X-Simulation-Scenario", "timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(airtimeRequest("onafriq-timeout-100")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("BX0001"));

        mockMvc.perform(post("/services/airtime/request")
                        .header("x-api-key", "onafriq-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String airtimeRequest(String reference) {
        return """
                {
                  "service_type": "mtn",
                  "plan": "prepaid",
                  "amount": 100,
                  "phone": "07034953306",
                  "agentId": "205",
                  "agentReference": "%s"
                }
                """.formatted(reference);
    }

    private String electricityRequest(String reference) {
        return """
                {
                  "service_type": "ikeja_electric_prepaid",
                  "account_number": "04042404139",
                  "amount": 2000,
                  "phone": "08034210294",
                  "agentId": "205",
                  "agentReference": "%s"
                }
                """.formatted(reference);
    }
}