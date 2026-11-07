package com.edgefort.simulator;

import com.edgefort.simulator.provider.nibss.nip.security.PgpKeys;
import com.edgefort.simulator.provider.nibss.nip.security.PgpSecretKeys;
import com.jayway.jsonpath.JsonPath;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
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
 * An inward credit the institution never receives is not recorded, so its status query finds no record.
 */
@SpringBootTest(properties = {
        "simulator.admin-token=unreachable-admin",
        "simulator.providers.nibss-nip.config.inward.timeout=2s"
})
@AutoConfigureMockMvc
class NibssNipInwardUnreachableIntegrationTest {

    private static final String ADMIN_TOKEN = "unreachable-admin";
    private static final PgpSecretKeys INSTITUTION_KEYS = PgpSecretKeys.generate("Test Institution");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void unreachableInstitution(DynamicPropertyRegistry registry) {
        registry.add("simulator.providers.nibss-nip.config.inward.url",
                () -> "http://127.0.0.1:" + closedPort() + "/NIPWS/BankInwardService");
    }

    @Test
    void unreachableInstitutionIsABadGatewayAndNothingIsRecorded() throws Exception {
        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(PgpKeys.binary(INSTITUTION_KEYS.publicKeyRing())))
                .andExpect(status().isNoContent());
        PGPPublicKeyRing simulatorKey = PgpKeys.parsePublicKeyRing(mockMvc.perform(
                        get("/admin/providers/nibss-nip/keys/simulator-public").header("X-Admin-Token", ADMIN_TOKEN))
                .andReturn().getResponse().getContentAsByteArray());

        String failure = mockMvc.perform(post("/admin/providers/nibss-nip/inward/credit")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "destinationInstitutionCode": "000058",
                                  "beneficiaryAccountName": "Ajibade Oluwasegun",
                                  "beneficiaryAccountNumber": "0123456789",
                                  "beneficiaryBankVerificationNumber": "22222222222",
                                  "originatorAccountName": "Adewale Hassan",
                                  "originatorAccountNumber": "3333002345",
                                  "originatorBankVerificationNumber": "1033000441",
                                  "amount": 100.50
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("INWARD_CALL_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.method").value("fundtransfersingleitem_dc"))
                .andExpect(jsonPath("$.requestXml").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String sessionId = JsonPath.read(failure, "$.sessionId");

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
        assertThat(xpath(response, "/TSQuerySingleResponse/ResponseCode")).isEqualTo("25");
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
