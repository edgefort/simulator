package com.edgefort.simulator;

import com.edgefort.simulator.support.NoResponseAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "simulator.admin-token=local-admin",
        "simulator.providers.onafriq-bill-payment.config.api-key=onafriq-test-key",
        "simulator.profiles.pending.transition-delay=20ms",
        "simulator.profiles.pending-then-failed.transition-delay=20ms",
        "simulator.profiles.timeout.delay=50ms",
        "simulator.profiles.timeout-not-received.delay=50ms"
})
@AutoConfigureMockMvc
class OnafriqBillPaymentIntegrationTest {

    private static final String PROVIDER = "onafriq-bill-payment";
    private static final String API_KEY = "onafriq-test-key";

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @BeforeEach
    void resetSimulator() throws Exception {
        mockMvc.perform(post("/admin/state/reset").header("X-Admin-Token", "local-admin"))
                .andExpect(status().isNoContent());
    }

    @Test
    void acceptsDocumentedApiKeyHeadersAndRejectsMissingOrBadCredentials() throws Exception {
        mockMvc.perform(get("/services/airtime/providers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0013"))
                .andExpect(jsonPath("$.message").value("Bearer Token not provided"));

        mockMvc.perform(get("/services/airtime/providers").header("x-api-key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("SEC00001"));

        mockMvc.perform(get("/services/airtime/providers").header("x-api-key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.providers[0].service_type").value("mtn"));

        mockMvc.perform(get("/services/airtime/providers")
                        .header(HttpHeaders.AUTHORIZATION, "Api-key " + API_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/services/airtime/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(airtimeRequest("onafriq-auth-100")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BX0013"));
    }

    @Test
    void exposesDocumentedDiscoveryRoutes() throws Exception {
        mockMvc.perform(get("/services/superagent/account/balance").header("x-api-key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").exists())
                .andExpect(jsonPath("$.data.lastDeposit").exists());

        mockMvc.perform(get("/services/billers/category/all").header("x-api-key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].service_type", hasItem("electricity")));

        mockMvc.perform(get("/services/billers/services/list").header("x-api-key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.epin[0].serviceName").exists());

        mockMvc.perform(json(post("/services/billers/services/category"), """
                        {"service_type": "electricity"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].serviceCategory").value("electricity"));

        mockMvc.perform(get("/services/electricity/billers").header("x-api-key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].service_types.ikeja_electric_prepaid").value("PREPAID"));

        mockMvc.perform(json(post("/services/databundle/bundles"), """
                        {"service_type": "mtn"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].datacode").exists());

        mockMvc.perform(json(post("/services/multichoice/addons"), """
                        {"service_type": "dstv", "product_code": "NNJ2E36"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].availablePricingOptions[0].monthsPaidFor").value(1));
    }

    @Test
    void successfulPurchaseIsRequeriedWithTheSameDataAndDuplicateReferenceIsRejected() throws Exception {
        String body = airtimeRequest("onafriq-airtime-100");

        String purchase = mockMvc.perform(json(post("/services/airtime/request"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Successful"))
                .andExpect(jsonPath("$.data.statusCode").value("0"))
                .andExpect(jsonPath("$.data.transactionStatus").value("success"))
                .andExpect(jsonPath("$.data.transactionReference", isA(Number.class)))
                .andExpect(jsonPath("$.data.transactionMessage").value("Airtime Topup successful on 07034953306"))
                .andExpect(jsonPath("$.data.agentReference").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(requery("onafriq-airtime-100"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(purchase));

        mockMvc.perform(get("/services/superagent/transaction/query")
                        .header("x-api-key", API_KEY)
                        .queryParam("agentReference", "onafriq-airtime-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionStatus").value("success"));

        mockMvc.perform(json(post("/services/airtime/request"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0023"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void failureSelectedThroughAdminIsReportedByPurchaseAndRequery() throws Exception {
        admin(post("/admin/overrides"), """
                {"provider": "%s", "operation": "airtime", "profile": "failure", "count": 1}
                """.formatted(PROVIDER)).andExpect(status().isCreated());

        mockMvc.perform(json(post("/services/airtime/request"), airtimeRequest("onafriq-failure-100")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0022"));

        mockMvc.perform(requery("onafriq-failure-100"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("BX0022"));

        mockMvc.perform(json(post("/services/airtime/request"), airtimeRequest("onafriq-after-override-100")))
                .andExpect(status().isOk());
    }

    @Test
    void pendingPurchaseCompletesAsSuccessOrFailedOnRequery() throws Exception {
        admin(post("/admin/overrides"), """
                {"provider": "%s", "reference": "onafriq-pending-100", "profile": "pending"}
                """.formatted(PROVIDER)).andExpect(status().isCreated());

        mockMvc.perform(json(post("/services/electricity/request"), electricityRequest("onafriq-pending-100", "04042404048")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.code").value("EXC00114"))
                .andExpect(jsonPath("$.data.transactionStatus").value("pending"));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                mockMvc.perform(requery("onafriq-pending-100"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("success"))
                        .andExpect(jsonPath("$.data.tokenCode").exists())
        );

        admin(post("/admin/overrides"), """
                {"provider": "%s", "reference": "onafriq-pending-200", "profile": "pending-then-failed"}
                """.formatted(PROVIDER)).andExpect(status().isCreated());

        mockMvc.perform(json(post("/services/airtime/request"), airtimeRequest("onafriq-pending-200")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                mockMvc.perform(requery("onafriq-pending-200"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value("BX0022"))
        );
    }

    @Test
    void reversedCompletionIsRejectedBecauseOnafriqCannotReportIt() throws Exception {
        admin(put("/admin/providers/%s/operations/airtime/profile".formatted(PROVIDER)), """
                {"profile": "pending-then-reversed"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void timeoutSendsNoResponseAndRequeryReportsTheRecordedTransaction() throws Exception {
        admin(post("/admin/overrides"), """
                {"provider": "%s", "reference": "onafriq-timeout-100", "profile": "timeout"}
                """.formatted(PROVIDER)).andExpect(status().isCreated());

        NoResponseAssertions.assertNoResponse(
                "POST",
                URI.create("http://localhost:%d/services/airtime/request".formatted(port)),
                Map.of("x-api-key", API_KEY, "Content-Type", MediaType.APPLICATION_JSON_VALUE),
                airtimeRequest("onafriq-timeout-100")
        );

        mockMvc.perform(requery("onafriq-timeout-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.transactionStatus").value("success"));
    }

    @Test
    void timeoutNotReceivedLeavesNoTransactionForRequery() throws Exception {
        admin(post("/admin/overrides"), """
                {"provider": "%s", "reference": "onafriq-timeout-200", "profile": "timeout-not-received"}
                """.formatted(PROVIDER)).andExpect(status().isCreated());

        NoResponseAssertions.assertNoResponse(
                "POST",
                URI.create("http://localhost:%d/services/airtime/request".formatted(port)),
                Map.of("x-api-key", API_KEY, "Content-Type", MediaType.APPLICATION_JSON_VALUE),
                airtimeRequest("onafriq-timeout-200")
        );

        mockMvc.perform(requery("onafriq-timeout-200"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0002"));
    }

    @Test
    void testDataRuleOnDocumentedFieldSelectsFailure() throws Exception {
        admin(post("/admin/rules"), """
                {"provider": "%s", "operation": "electricity", "field": "account_number",
                 "equals": "04042404139", "profile": "failure"}
                """.formatted(PROVIDER)).andExpect(status().isCreated());

        mockMvc.perform(json(post("/services/electricity/request"), electricityRequest("onafriq-rule-100", "04042404139")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("BX0022"));

        mockMvc.perform(json(post("/services/electricity/request"), electricityRequest("onafriq-rule-200", "04042404048")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenCode").exists())
                .andExpect(jsonPath("$.data.tokenAmount").value(2000));
    }

    @Test
    void nameFinderValidationSucceedsOrFailsWithDocumentedCode() throws Exception {
        mockMvc.perform(json(post("/services/namefinder/query"), """
                        {"service_type": "ikeja_electric_postpaid", "account_number": "04042404139"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.name").exists())
                .andExpect(jsonPath("$.data.user.accountNumber").value("04042404139"));

        admin(post("/admin/overrides"), """
                {"provider": "%s", "operation": "name-finder", "profile": "failure", "count": 1}
                """.formatted(PROVIDER)).andExpect(status().isCreated());

        mockMvc.perform(json(post("/services/namefinder/query"), """
                        {"service_type": "dstv", "account_number": "4131953321"}
                        """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0025"));
    }

    @Test
    void epinPurchaseReturnsTheRequestedNumberOfPins() throws Exception {
        mockMvc.perform(json(post("/services/epin/request"), """
                        {"service_type": "glo", "amount": 200, "pinValue": 100, "numberOfPins": 2,
                         "agentId": "205", "agentReference": "onafriq-epin-100"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionMessage").value("Successful pin purchase"))
                .andExpect(jsonPath("$.data.pins", hasSize(2)))
                .andExpect(jsonPath("$.data.pins[0].pin").exists())
                .andExpect(jsonPath("$.data.pins[0].serialNumber").exists())
                .andExpect(jsonPath("$.data.pins[0].expiresOn").exists());
    }

    @Test
    void rejectsInvalidRequestsInTheDocumentedErrorFormat() throws Exception {
        mockMvc.perform(json(post("/services/airtime/request"), "{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0003"))
                .andExpect(jsonPath("$.errors", hasItem("service_type must not be blank")));

        mockMvc.perform(json(post("/services/airtime/request"), "{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("BX0003"));

        mockMvc.perform(get("/services/superagent/transaction/requery").header("x-api-key", API_KEY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BX0003"));
    }

    private ResultActions admin(
            MockHttpServletRequestBuilder request,
            String body
    ) throws Exception {
        return mockMvc.perform(request
                .header("X-Admin-Token", "local-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder request,
            String body
    ) {
        return request.header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder requery(
            String agentReference
    ) {
        return get("/services/superagent/transaction/requery")
                .header("x-api-key", API_KEY)
                .queryParam("agentReference", agentReference);
    }

    private static String airtimeRequest(String reference) {
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

    private static String electricityRequest(String reference, String accountNumber) {
        return """
                {
                  "service_type": "ikeja_electric_prepaid",
                  "account_number": "%s",
                  "amount": 2000,
                  "phone": "08034210294",
                  "agentId": "205",
                  "agentReference": "%s"
                }
                """.formatted(accountNumber, reference);
    }
}
