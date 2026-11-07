package com.edgefort.simulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

/**
 * Behavior that spans providers: simultaneous use, the control plane, and the absence of simulator parameters on
 * provider surfaces.
 */
@SpringBootTest(properties = "simulator.admin-token=local-admin")
@AutoConfigureMockMvc
class ProviderApiIntegrationTest {

    private static final String ADMIN_TOKEN = "local-admin";
    private static final String INTERSWITCH_API = "/quicktellerservice/api/v5";

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void reset() throws Exception {
        mockMvc.perform(admin(post("/admin/state/reset"))).andExpect(status().isNoContent());
    }

    @Test
    void providersRunSimultaneouslyWithIndependentBehaviorAndIsolatedReferences() throws Exception {
        String reference = "shared-reference-1";
        mockMvc.perform(admin(put("/admin/providers/nibss-nip/profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"failure\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(nibssTransfer(reference))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("96"));
        mockMvc.perform(interswitchTransfer(reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResponseCode").value("90000"));
        mockMvc.perform(onafriqAirtime(reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void simulatorControlHeadersAreIgnoredByProviderSurfaces() throws Exception {
        mockMvc.perform(nibssTransfer("header-reference-1").header("X-Simulation-Scenario", "failure"))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("00"));
        mockMvc.perform(onafriqAirtime("header-reference-1").header("X-Simulation-Scenario", "failure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void controlPlaneRejectsBehaviorTheProviderSpecificationCannotExpress() throws Exception {
        mockMvc.perform(admin(put("/admin/providers/nibss-nip/operations/fund-transfer-direct-credit/profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"pending-then-reversed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SIMULATION_CONTROL"))
                .andExpect(jsonPath("$.message").value(containsString("REVERSED")));
        mockMvc.perform(admin(put("/admin/providers/nibss-nip/operations/name-enquiry/profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"pending\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no pending response")));
        mockMvc.perform(admin(post("/admin/overrides"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"unknown\",\"profile\":\"failure\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown provider: unknown"));
        mockMvc.perform(admin(post("/admin/rules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"nibss-nip","operation":"name-enquiry","field":"Amount",
                                 "equals":"1","profile":"failure"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not a match field")));
    }

    @Test
    void controlPlaneDescribesEveryProviderAndItsOperationCapabilities() throws Exception {
        mockMvc.perform(admin(get("/admin/providers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(
                        "interswitch", "nibss-nip", "onafriq-bill-payment"
                )))
                .andExpect(jsonPath("$[?(@.id == 'nibss-nip')].operations[?(@.name == 'fund-transfer-direct-credit')]"
                        + ".queryStatuses[*]").value(not(hasItem("REVERSED"))))
                .andExpect(jsonPath("$[?(@.id == 'interswitch')].operations[?(@.name == 'transfer')]"
                        + ".queryStatuses[*]").value(hasItem("REVERSED")));
    }

    @Test
    void resetRestoresConfiguredBehaviorAndClearsOverrides() throws Exception {
        mockMvc.perform(admin(put("/admin/providers/onafriq-bill-payment/profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"failure\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(admin(post("/admin/overrides"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"nibss-nip\",\"reference\":\"reset-reference-1\",\"profile\":\"failure\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(admin(delete("/admin/state"))).andExpect(status().isNoContent());

        mockMvc.perform(onafriqAirtime("reset-reference-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
        mockMvc.perform(nibssTransfer("reset-reference-1"))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("00"));
        mockMvc.perform(admin(get("/admin/overrides")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void controlPlaneRequiresTheAdminToken() throws Exception {
        mockMvc.perform(get("/admin/providers")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("X-Admin-Token", ADMIN_TOKEN);
    }

    private MockHttpServletRequestBuilder nibssTransfer(String sessionId) {
        return post("/nip/v9.4/fundtransfersingleitem_dc")
                .contentType(MediaType.APPLICATION_XML)
                .content("""
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
                        """.formatted(sessionId));
    }

    private MockHttpServletRequestBuilder interswitchTransfer(String transferCode) throws Exception {
        String mac = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512")
                .digest("100000566CA100000566ACNG".getBytes(StandardCharsets.UTF_8)));
        return post(INTERSWITCH_API + "/transactions/TransferFunds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer simulator-access-token")
                .header("TerminalId", "3PBL0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "transferCode": "%s",
                          "mac": "%s",
                          "termination": {
                            "amount": "100000",
                            "accountReceivable": {"accountNumber": "3001155245", "accountType": "00"},
                            "entityCode": "044",
                            "currencyCode": "566",
                            "paymentMethodCode": "AC",
                            "countryCode": "NG"
                          },
                          "sender": {"phone": "08124888436", "email": "jane@example.com",
                                     "lastname": "Doe", "othernames": "Jane"},
                          "initiatingEntityCode": "PBL",
                          "initiation": {"amount": "100000", "currencyCode": "566",
                                         "paymentMethodCode": "CA", "channel": "7"},
                          "beneficiary": {"lastname": "Smith", "othernames": "John"}
                        }
                        """.formatted(transferCode, mac));
    }

    private MockHttpServletRequestBuilder onafriqAirtime(String agentReference) {
        return post("/services/airtime/request")
                .header("x-api-key", "simulator-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "service_type": "mtn",
                          "plan": "prepaid",
                          "amount": 100,
                          "phone": "07034953306",
                          "agentId": "205",
                          "agentReference": "%s"
                        }
                        """.formatted(agentReference));
    }
}
