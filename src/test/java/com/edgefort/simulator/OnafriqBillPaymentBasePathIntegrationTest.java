package com.edgefort.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "simulator.providers.onafriq-bill-payment.base-path=/onafriq",
        "simulator.providers.onafriq-bill-payment.config.api-key=onafriq-test-key"
})
@AutoConfigureMockMvc
class OnafriqBillPaymentBasePathIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mountsDocumentedPathsUnderTheBasePathAndStillRequiresTheApiKey() throws Exception {
        String body = """
                {
                  "service_type": "mtn",
                  "plan": "prepaid",
                  "amount": 100,
                  "phone": "07034953306",
                  "agentId": "205",
                  "agentReference": "onafriq-base-path-100"
                }
                """;

        mockMvc.perform(post("/onafriq/services/airtime/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0013"));

        mockMvc.perform(post("/onafriq/services/airtime/request")
                        .header("x-api-key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SEC00001"));

        mockMvc.perform(post("/onafriq/services/airtime/request")
                        .header("x-api-key", "onafriq-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(get("/onafriq/services/superagent/transaction/requery")
                        .header("x-api-key", "onafriq-test-key")
                        .queryParam("agentReference", "onafriq-base-path-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionStatus").value("success"));

        mockMvc.perform(get("/services/airtime/providers").header("x-api-key", "onafriq-test-key"))
                .andExpect(status().isNotFound());
    }
}
