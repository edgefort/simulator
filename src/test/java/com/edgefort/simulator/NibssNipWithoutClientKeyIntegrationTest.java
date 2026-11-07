package com.edgefort.simulator;

import com.edgefort.simulator.provider.nibss.nip.security.PgpKeys;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static com.edgefort.simulator.support.NipSoapMessages.encryptedEnvelope;
import static com.edgefort.simulator.support.NipSoapMessages.faultCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without the institution public key the simulator cannot encrypt answers or inward requests. This context never
 * receives a client key.
 */
@SpringBootTest(properties = {
        "simulator.admin-token=no-client-key-admin",
        "simulator.providers.nibss-nip.config.inward.url=http://127.0.0.1:9/NIPWS/BankInwardService"
})
@AutoConfigureMockMvc
class NibssNipWithoutClientKeyIntegrationTest {

    private static final String ADMIN_TOKEN = "no-client-key-admin";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void soapRequestIsAServerFaultAndNothingIsSimulated() throws Exception {
        PGPPublicKeyRing simulatorKey = PgpKeys.parsePublicKeyRing(mockMvc.perform(
                        get("/admin/providers/nibss-nip/keys/simulator-public").header("X-Admin-Token", ADMIN_TOKEN))
                .andReturn().getResponse().getContentAsByteArray());

        String fault = mockMvc.perform(post("/NIPWS/NIPInterface")
                        .contentType("text/xml;charset=UTF-8")
                        .content(encryptedEnvelope("fundtransfersingleitem_dc", "<FTSingleCreditRequest/>",
                                simulatorKey)))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(faultCode(fault)).isEqualTo("S:Server");

        mockMvc.perform(get("/admin/providers/nibss-nip/keys").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulator.fingerprint").isNotEmpty())
                .andExpect(jsonPath("$.client").doesNotExist());
    }

    @Test
    void inwardCallsAreRejectedWithoutTheInstitutionKey() throws Exception {
        mockMvc.perform(post("/admin/providers/nibss-nip/inward/name-enquiry")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationInstitutionCode":"000058","accountNumber":"0123456789"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INWARD_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
