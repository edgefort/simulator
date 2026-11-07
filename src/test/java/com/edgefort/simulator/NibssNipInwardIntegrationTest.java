package com.edgefort.simulator;

import com.edgefort.simulator.provider.nibss.nip.security.PgpKeys;
import com.edgefort.simulator.provider.nibss.nip.security.PgpSecretKeys;
import com.edgefort.simulator.support.FakeInstitutionServer;
import com.jayway.jsonpath.JsonPath;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static com.edgefort.simulator.support.NipSoapMessages.decryptReturn;
import static com.edgefort.simulator.support.NipSoapMessages.encryptedEnvelope;
import static com.edgefort.simulator.support.NipSoapMessages.xpath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The simulator plays NIBSS against a fake institution inward web service: FT credit, then the institution's
 * outward status query decides whether it may credit the beneficiary.
 */
@SpringBootTest(properties = "simulator.admin-token=inward-admin")
@AutoConfigureMockMvc
class NibssNipInwardIntegrationTest {

    private static final String ADMIN_TOKEN = "inward-admin";
    private static final PgpSecretKeys INSTITUTION_KEYS = PgpSecretKeys.generate("Test Institution");
    private static final FakeInstitutionServer INSTITUTION = new FakeInstitutionServer(INSTITUTION_KEYS);

    @Autowired
    private MockMvc mockMvc;

    private PGPPublicKeyRing simulatorKey;

    @DynamicPropertySource
    static void institutionUrl(DynamicPropertyRegistry registry) {
        registry.add("simulator.providers.nibss-nip.config.inward.url", INSTITUTION::url);
        registry.add("simulator.providers.nibss-nip.config.inward.source-institution-code", () -> "999058");
    }

    @AfterAll
    static void stopInstitution() {
        INSTITUTION.close();
    }

    @BeforeEach
    void exchangeKeys() throws Exception {
        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(PgpKeys.binary(INSTITUTION_KEYS.publicKeyRing())))
                .andExpect(status().isNoContent());
        simulatorKey = PgpKeys.parsePublicKeyRing(mockMvc.perform(
                        get("/admin/providers/nibss-nip/keys/simulator-public").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        INSTITUTION.simulatorKey(simulatorKey);
        INSTITUTION.respondWith("00");
    }

    @AfterEach
    void resetSimulator() throws Exception {
        mockMvc.perform(post("/admin/state/reset").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void approvedInwardCreditIsConfirmedByTheInstitutionStatusQuery() throws Exception {
        String response = credit()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("fundtransfersingleitem_dc"))
                .andExpect(jsonPath("$.url").value(INSTITUTION.url()))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.transactionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.statusQueryResponseCode").value("00"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String sessionId = JsonPath.read(response, "$.sessionId");
        assertThat(sessionId).hasSize(30).startsWith("999058").matches("\\d{30}");
        assertThat(JsonPath.<String>read(response, "$.responseXml")).contains("<ResponseCode>00</ResponseCode>");

        FakeInstitutionServer.Received received = INSTITUTION.lastRequest();
        assertThat(received.method()).isEqualTo("fundtransfersingleitem_dc");
        assertThat(received.namespace()).isEqualTo("http://core.nip.nibss/");
        assertThat(received.contentType()).startsWith("text/xml");
        assertThat(received.soapAction()).isEqualTo("\"\"");
        assertThat(received.message()).isEqualTo(JsonPath.read(response, "$.requestXml"));
        assertThat(xpath(received.message(), "/FTSingleCreditRequest/SessionID")).isEqualTo(sessionId);
        assertThat(xpath(received.message(), "/FTSingleCreditRequest/DestinationInstitutionCode")).isEqualTo("000058");
        assertThat(xpath(received.message(), "/FTSingleCreditRequest/ChannelCode")).isEqualTo("1");
        assertThat(xpath(received.message(), "/FTSingleCreditRequest/BeneficiaryAccountNumber"))
                .isEqualTo("0123456789");
        assertThat(xpath(received.message(), "/FTSingleCreditRequest/Amount")).isEqualTo("5000.00");
        assertThat(xpath(received.message(), "count(/FTSingleCreditRequest/NameEnquiryRef)")).isEqualTo("0");

        assertThat(institutionStatusQuery(sessionId)).isEqualTo("00");
    }

    @Test
    void failureSelectedForTheInwardOperationTellsTheInstitutionNotToCredit() throws Exception {
        mockMvc.perform(post("/admin/overrides")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"nibss-nip","operation":"inward-fund-transfer-direct-credit",
                                 "profile":"failure"}
                                """))
                .andExpect(status().isCreated());

        String response = credit()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.transactionStatus").value("FAILED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(institutionStatusQuery(JsonPath.read(response, "$.sessionId"))).isEqualTo("96");
    }

    @Test
    void creditTheInstitutionDidNotApproveIsReportedAsFailed() throws Exception {
        INSTITUTION.respondWith("51");

        String response = credit()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("51"))
                .andExpect(jsonPath("$.transactionStatus").value("FAILED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(institutionStatusQuery(JsonPath.read(response, "$.sessionId"))).isEqualTo("96");
    }

    @Test
    void inwardNameEnquiryReturnsTheInstitutionResponse() throws Exception {
        mockMvc.perform(post("/admin/providers/nibss-nip/inward/name-enquiry")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationInstitutionCode":"000058","accountNumber":"0123456789"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("nameenquirysingleitem"))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.transactionStatus").doesNotExist())
                .andExpect(jsonPath("$.responseXml").value(
                        org.hamcrest.Matchers.containsString("<AccountName>INSTITUTION CUSTOMER</AccountName>")));

        assertThat(INSTITUTION.lastRequest().method()).isEqualTo("nameenquirysingleitem");
        assertThat(xpath(INSTITUTION.lastRequest().message(), "/NESingleRequest/AccountNumber"))
                .isEqualTo("0123456789");
    }

    @Test
    void invalidInwardRequestsAreRejectedWithAdminErrors() throws Exception {
        mockMvc.perform(post("/admin/providers/nibss-nip/inward/credit")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationInstitutionCode\":\"58\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.amount").exists());

        mockMvc.perform(post("/admin/providers/nibss-nip/inward/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creditBody()))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions credit() throws Exception {
        return mockMvc.perform(post("/admin/providers/nibss-nip/inward/credit")
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(creditBody()));
    }

    private static String creditBody() {
        return """
                {
                  "destinationInstitutionCode": "000058",
                  "beneficiaryAccountName": "Ajibade Oluwasegun",
                  "beneficiaryAccountNumber": "0123456789",
                  "beneficiaryBankVerificationNumber": "22222222222",
                  "beneficiaryKycLevel": "1",
                  "originatorAccountName": "Adewale Hassan",
                  "originatorAccountNumber": "3333002345",
                  "originatorBankVerificationNumber": "1033000441",
                  "originatorKycLevel": "1",
                  "narration": "Inward credit",
                  "paymentReference": "inward-reference-1",
                  "amount": 5000
                }
                """;
    }

    /**
     * The institution's outward status query (016) to NIBSS for an inward credit.
     */
    private String institutionStatusQuery(String sessionId) throws Exception {
        String soap = mockMvc.perform(post("/NIPWS/NIPTSQInterface")
                        .contentType("text/xml;charset=UTF-8")
                        .content(encryptedEnvelope("txnstatusquerysingleitem", """
                                <TSQuerySingleRequest>
                                  <SourceInstitutionCode>000058</SourceInstitutionCode>
                                  <ChannelCode>1</ChannelCode>
                                  <SessionID>%s</SessionID>
                                </TSQuerySingleRequest>
                                """.formatted(sessionId), simulatorKey)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String response = decryptReturn(soap, "txnstatusquerysingleitem", INSTITUTION_KEYS);
        return xpath(response, "/TSQuerySingleResponse/ResponseCode");
    }
}
