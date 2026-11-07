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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bills Payment and Airtime with the provider mounted under {@code simulator.providers.interswitch.base-path}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "simulator.admin-token=local-admin",
                "simulator.providers.interswitch.base-path=/isw",
                "simulator.providers.interswitch.config.client-id=bills-client",
                "simulator.providers.interswitch.config.client-secret=bills-secret",
                "simulator.providers.interswitch.config.access-token=bills-access-token",
                "simulator.providers.interswitch.config.terminal-id=3BIL0001",
                "simulator.profiles.pending.transition-delay=20ms",
                "simulator.profiles.pending-then-failed.transition-delay=20ms",
                "simulator.profiles.pending-then-reversed.transition-delay=20ms",
                "simulator.profiles.timeout.delay=50ms",
                "simulator.profiles.timeout-then-failed.delay=50ms",
                "simulator.profiles.timeout-not-received.delay=50ms"
        }
)
@AutoConfigureMockMvc
class InterswitchBillPaymentApiIntegrationTest {

    private static final String BASE_PATH = "/isw";
    private static final String API = BASE_PATH + "/quicktellerservice/api/v5";
    private static final String ADMIN_TOKEN = "local-admin";
    private static final String TERMINAL_ID = "3BIL0001";
    private static final String BEARER = "Bearer bills-access-token";

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
    void mountsUnderTheBasePathWithOAuthAndBearerStillEnforced() throws Exception {
        mockMvc.perform(get("/quicktellerservice/api/v5/services/categories")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/passport/oauth/token")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(BASE_PATH + "/passport/oauth/token")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("bills-access-token"));

        mockMvc.perform(get(API + "/services/categories").header("TerminalId", TERMINAL_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ResponseCode").value("10003"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        mockMvc.perform(get(API + "/services/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-token")
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ResponseCode").value("60003"));

        mockMvc.perform(get(API + "/services/categories")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", "3PBL0001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("70012"));
    }

    @Test
    void exposesThePublishedCategoryBillerAndPaymentItemCatalogs() throws Exception {
        authorized(get(API + "/services/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BillerCategories[?(@.Id == 1)].Name").value("Utility Bills"))
                .andExpect(jsonPath("$.BillerCategories[?(@.Id == 4)].Name").value("Mobile/Recharge"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("SUCCESSFUL"));

        authorized(get(API + "/services").queryParam("categoryId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BillerList.Count").value(3))
                .andExpect(jsonPath("$.BillerList.Category[0].Id").value(4))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[?(@.Id == 109)].ProductCode")
                        .value("628051043"))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[0].CurrencyCode").value("566"))
                .andExpect(jsonPath("$.BillerList.Category[0].Billers[0].Type").value("MO"));

        authorized(get(API + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BillerList.Count").value(4))
                .andExpect(jsonPath("$.BillerList.Category.length()").value(2));

        authorized(get(API + "/services/options").queryParam("serviceid", "17589"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PaymentItems[0].PaymentCode").value("051758901"))
                .andExpect(jsonPath("$.PaymentItems[0].BillerId").value("17589"))
                .andExpect(jsonPath("$.PaymentItems[0].IsAmountFixed").value(true))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        authorized(get(API + "/services/options").queryParam("serviceid", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"));
    }

    @Test
    void validatesCustomersAndHonoursTheCustomerValidationProfile() throws Exception {
        validate(validationRequest("051758901", "12345678910", TERMINAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Customers[0].TerminalId").value(TERMINAL_ID))
                .andExpect(jsonPath("$.Customers[0].PaymentCode").value("051758901"))
                .andExpect(jsonPath("$.Customers[0].CustomerId").value("12345678910"))
                .andExpect(jsonPath("$.Customers[0].FullName").value("SIMULATED CUSTOMER"))
                .andExpect(jsonPath("$.Customers[0].ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        validate(validationRequest("unknown", "12345678910", TERMINAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Customers[0].ResponseCode").value("90007"))
                .andExpect(jsonPath("$.ResponseCode").value("90008"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        admin("/admin/rules", """
                {"provider":"interswitch","operation":"customer-validation","field":"CustomerId","equals":"99999999999","profile":"failure"}
                """).andExpect(status().isCreated());
        validate(validationRequest("051758901", "99999999999", TERMINAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Customers[0].ResponseCode").value("90002"))
                .andExpect(jsonPath("$.Customers[0].FullName").doesNotExist())
                .andExpect(jsonPath("$.ResponseCode").value("90008"));

        validate(validationRequest("051758901", "12345678910", "3PBL0001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("70012"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        validate("{\"customers\":[],\"TerminalId\":\"" + TERMINAL_ID + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"));
    }

    @Test
    void timeoutOnCustomerValidationClosesTheConnection() throws Exception {
        admin("/admin/overrides", """
                {"provider":"interswitch","operation":"customer-validation","profile":"timeout","count":1}
                """).andExpect(status().isCreated());
        assertNoResponse("/Transactions/validatecustomers", validationRequest("051758901", "1", TERMINAL_ID));
    }

    @Test
    void purchasesBillsAndAirtimeAndQueriesThePublishedStatus() throws Exception {
        pay(paymentRequest("051758901", "12345678910", "100000", "145300000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TransactionRef").value("PBL|Web|3BIL0001|AbjBuyPw|145300000001"))
                .andExpect(jsonPath("$.RechargePIN").isString())
                .andExpect(jsonPath("$.AdditionalInfo.Token").isString())
                .andExpect(jsonPath("$.ApprovedAmount").value("100000"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"))
                .andExpect(jsonPath("$.ResponseDescription").value("Success"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("SUCCESSFUL"));

        query("145300000001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BillPayment.biller").value("AbjBuyPw"))
                .andExpect(jsonPath("$.BillPayment.customerId1").value("12345678910"))
                .andExpect(jsonPath("$.BillPayment.paymentTypeCode").value("051758901"))
                .andExpect(jsonPath("$.BillPayment.billerId").value("17589"))
                .andExpect(jsonPath("$.amount").value("100000"))
                .andExpect(jsonPath("$.currencyCode").value("566"))
                .andExpect(jsonPath("$.customer").value("12345678910"))
                .andExpect(jsonPath("$.customerMobile").value("2348012345678"))
                .andExpect(jsonPath("$.requestReference").value("145300000001"))
                .andExpect(jsonPath("$.serviceCode").value("051758901"))
                .andExpect(jsonPath("$.serviceProviderId").value("17589"))
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.transactionRef").value("PBL|Web|3BIL0001|AbjBuyPw|145300000001"))
                .andExpect(jsonPath("$.transactionResponseCode").value("90000"))
                .andExpect(jsonPath("$.transactionSet").value("BillPayment"))
                .andExpect(jsonPath("$.RechargePIN").doesNotExist());

        mockMvc.perform(post(API + "/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalID", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "TerminalId": "%s",
                                  "paymentCode": "628051043",
                                  "customerId": "2348012345678",
                                  "customerMobile": "2348012345678",
                                  "customerEmail": "customer@example.com",
                                  "amount": "50000",
                                  "requestReference": "145300000002"
                                }
                                """.formatted(TERMINAL_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ApprovedAmount").value("50000"))
                .andExpect(jsonPath("$.RechargePIN").doesNotExist())
                .andExpect(jsonPath("$.AdditionalInfo.network").value("MTN"))
                .andExpect(jsonPath("$.AdditionalInfo.phoneNumber").value("2348012345678"))
                .andExpect(jsonPath("$.AdditionalInfo.amountCredited").value("500.00"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));
        query("145300000002").andExpect(jsonPath("$.customerEmail").value("customer@example.com"));
    }

    @Test
    void acceptsThePascalCasePaymentFieldsPublishedInTheAdditionalInfoExample() throws Exception {
        mockMvc.perform(post(API + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "CustomerId": "2348012345678",
                                  "PaymentCode": "628051045",
                                  "Amount": "250000",
                                  "CustomerEmail": "test@yopmail.com",
                                  "CustomerMobile": "07032040511",
                                  "CustomerName": "John Doe",
                                  "AdditionalInfo": "Teller Name:James Etim;Branch Name:VI",
                                  "RequestReference": "145300000003"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AdditionalInfo.network").value("GLO"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));
    }

    @Test
    void returnsDuplicateAndProductErrorsWithPublishedCodes() throws Exception {
        String request = paymentRequest("628051043", "2348012345678", "50000", "145300000010");
        pay(request).andExpect(jsonPath("$.ResponseCode").value("90000"));
        pay(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TransactionRef").value("PBL|Web|3BIL0001|MTNVTU1|145300000010"))
                .andExpect(jsonPath("$.ResponseCode").value("90005"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        pay(paymentRequest("unknown", "12345678910", "50000", "145300000011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90007"))
                .andExpect(jsonPath("$.ResponseDescription").value("Invalid payment code"));

        pay(paymentRequest("051758901", "12345678910", "34999", "145300000012"))
                .andExpect(jsonPath("$.ResponseCode").value("90003"))
                .andExpect(jsonPath("$.ResponseDescription").value("Invalid amount"));
        pay(paymentRequest("628051043", "2348012345678", "1000001", "145300000013"))
                .andExpect(jsonPath("$.ResponseCode").value("90003"));
        query("145300000013").andExpect(jsonPath("$.ResponseCode").value("70018"));
    }

    @Test
    void rejectsInvalidPaymentRequestsInTheInterswitchEnvelope() throws Exception {
        pay(paymentRequest("628051043", "2348012345678", "50000", "reference-is-too-long-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"))
                .andExpect(jsonPath("$.ResponseDescription").value("Invalid Request"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));

        pay("{\"paymentCode\":\"628051043\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"));

        pay(paymentRequest("628051043", "2348012345678", "fifty", "145300000020"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"));

        pay(paymentRequest("628051043", "2348012345678", "50000", "145300000021")
                .replace("\"paymentCode\"", "\"TerminalId\": \"3PBL0001\", \"paymentCode\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("70012"));

        mockMvc.perform(get(API + "/Transactions")
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header("TerminalId", TERMINAL_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("10002"));
    }

    @Test
    void reportsFailureSelectedByARuleOnTheCustomerId() throws Exception {
        admin("/admin/rules", """
                {"provider":"interswitch","operation":"bill-payment","field":"customerId","equals":"2348000000000","profile":"failure"}
                """).andExpect(status().isCreated());

        pay(paymentRequest("628051043", "2348000000000", "50000", "145300000030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TransactionRef").isString())
                .andExpect(jsonPath("$.ResponseCode").value("90006"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("FAILED"));
        query("145300000030")
                .andExpect(jsonPath("$.status").value("Failed"))
                .andExpect(jsonPath("$.transactionResponseCode").value("90006"));

        pay(paymentRequest("628051043", "2348012345678", "50000", "145300000031"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));
    }

    @Test
    void completesPendingPaymentsAsCompletedFailedOrReversed() throws Exception {
        admin("/admin/overrides", """
                {"provider":"interswitch","operation":"bill-payment","profile":"pending","count":1}
                """).andExpect(status().isCreated());
        pay(paymentRequest("628051043", "2348012345678", "50000", "145300000040"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90009"))
                .andExpect(jsonPath("$.ResponseCodeGrouping").value("PENDING"));
        awaitQueryStatus("145300000040", "Completed");

        override("145300000041", "pending-then-failed");
        pay(paymentRequest("628051043", "2348012345678", "50000", "145300000041"))
                .andExpect(jsonPath("$.ResponseCode").value("90009"));
        awaitQueryStatus("145300000041", "Failed");

        override("145300000042", "pending-then-reversed");
        pay(paymentRequest("628051043", "2348012345678", "50000", "145300000042"))
                .andExpect(jsonPath("$.ResponseCode").value("90009"));
        awaitQueryStatus("145300000042", "Reversed");
    }

    @Test
    void timeoutClosesTheConnectionAndTheQueryReportsTheRecordedPayment() throws Exception {
        override("145300000050", "timeout");
        assertNoResponse("/Transactions", paymentRequest("628051043", "2348012345678", "50000", "145300000050"));
        awaitQueryStatus("145300000050", "Completed");

        override("145300000051", "timeout-then-failed");
        assertNoResponse("/Transactions", paymentRequest("628051043", "2348012345678", "50000", "145300000051"));
        awaitQueryStatus("145300000051", "Failed");

        override("145300000052", "timeout-not-received");
        assertNoResponse("/Transactions", paymentRequest("628051043", "2348012345678", "50000", "145300000052"));
        query("145300000052")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ResponseCode").value("70018"));
    }

    @Test
    void resetClearsQueryStateSoAReferenceCanBeReused() throws Exception {
        pay(paymentRequest("628051043", "2348012345678", "50000", "145300000060"))
                .andExpect(jsonPath("$.ResponseCode").value("90000"));

        mockMvc.perform(post("/admin/state/reset").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
        query("145300000060").andExpect(jsonPath("$.ResponseCode").value("70018"));

        pay(paymentRequest("628051045", "2348012345678", "50000", "145300000060"))
                .andExpect(jsonPath("$.AdditionalInfo.network").value("GLO"));
        query("145300000060").andExpect(jsonPath("$.serviceCode").value("628051045"));
    }

    private ResultActions authorized(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .header("TerminalId", TERMINAL_ID));
    }

    private ResultActions validate(String body) throws Exception {
        return authorized(post(API + "/Transactions/validatecustomers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions pay(String body) throws Exception {
        return authorized(post(API + "/Transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions query(String reference) throws Exception {
        return authorized(get(API + "/Transactions").queryParam("requestRef", reference));
    }

    private ResultActions admin(String path, String json) throws Exception {
        return mockMvc.perform(post(path)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void override(String reference, String profile) throws Exception {
        admin("/admin/overrides", """
                {"provider":"interswitch","reference":"%s","profile":"%s"}
                """.formatted(reference, profile)).andExpect(status().isCreated());
    }

    private void awaitQueryStatus(String reference, String expected) {
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                query(reference).andExpect(jsonPath("$.status").value(expected))
        );
    }

    private void assertNoResponse(String path, String body) {
        NoResponseAssertions.assertNoResponse(
                "POST",
                URI.create("http://localhost:" + port + API + path),
                Map.of(
                        HttpHeaders.AUTHORIZATION, BEARER,
                        "TerminalId", TERMINAL_ID,
                        HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE
                ),
                body
        );
    }

    private static String basicCredentials() {
        return "Basic " + Base64.getEncoder()
                .encodeToString("bills-client:bills-secret".getBytes(StandardCharsets.UTF_8));
    }

    private static String validationRequest(String paymentCode, String customerId, String terminalId) {
        return """
                {
                  "customers": [{
                    "PaymentCode": "%s",
                    "CustomerId": "%s",
                    "WithDetails": true
                  }],
                  "TerminalId": "%s"
                }
                """.formatted(paymentCode, customerId, terminalId);
    }

    private static String paymentRequest(String paymentCode, String customerId, String amount, String reference) {
        return """
                {
                  "paymentCode": "%s",
                  "customerId": "%s",
                  "customerMobile": "2348012345678",
                  "amount": "%s",
                  "requestReference": "%s"
                }
                """.formatted(paymentCode, customerId, amount, reference);
    }
}
