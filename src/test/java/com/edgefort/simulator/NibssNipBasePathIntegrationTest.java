package com.edgefort.simulator;

import com.edgefort.simulator.provider.nibss.nip.security.PgpKeys;
import com.edgefort.simulator.provider.nibss.nip.security.PgpSecretKeys;
import com.edgefort.simulator.support.NipSoapMessages;
import com.edgefort.simulator.support.PgpTestKeys;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static com.edgefort.simulator.support.NipSoapMessages.decryptReturn;
import static com.edgefort.simulator.support.NipSoapMessages.encryptedEnvelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest(properties = "simulator.providers.nibss-nip.base-path=/nibss")
@AutoConfigureMockMvc
class NibssNipBasePathIntegrationTest {

    private static final String ADMIN_TOKEN = "simulator";

    private static final String NAME_ENQUIRY = """
            <NESingleRequest>
              <SessionID>base-path-session-001</SessionID>
              <DestinationInstitutionCode>000002</DestinationInstitutionCode>
              <ChannelCode>1</ChannelCode>
              <AccountNumber>2222000000012345</AccountNumber>
            </NESingleRequest>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mountsTheNipMessagesUnderTheConfiguredBasePath() throws Exception {
        mockMvc.perform(post("/nibss/nip/v9.4/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(NAME_ENQUIRY))
                .andExpect(status().isOk())
                .andExpect(xpath("/NESingleResponse/SessionID").string("base-path-session-001"))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("00"));

        mockMvc.perform(post("/nip/v9.4/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(NAME_ENQUIRY))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsNipMediaTypeAndFormatErrorHandlingUnderTheBasePath() throws Exception {
        mockMvc.perform(post("/nibss/nip/v9.4/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("30"));

        mockMvc.perform(post("/nibss/nip/v9.4/txnstatusquerysingleitem")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<TSQuerySingleRequest/>"))
                .andExpect(status().isOk())
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("30"));
    }

    @Test
    void mountsTheSoapServiceAndItsWsdlUnderTheBasePath() throws Exception {
        PgpSecretKeys institutionKeys = PgpTestKeys.generate("Base Path Institution");
        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(PgpKeys.binary(institutionKeys.publicKeyRing())))
                .andExpect(status().isNoContent());
        PGPPublicKeyRing simulatorKey = PgpKeys.parsePublicKeyRing(mockMvc.perform(
                        get("/admin/providers/nibss-nip/keys/simulator-public").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        String envelope = encryptedEnvelope("nameenquirysingleitem", NAME_ENQUIRY, simulatorKey);

        String soap = mockMvc.perform(post("/nibss/NIPWS/NIPInterface")
                        .contentType("text/xml;charset=UTF-8")
                        .content(envelope))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String response = decryptReturn(soap, "nameenquirysingleitem", institutionKeys);
        assertThat(NipSoapMessages.xpath(response, "/NESingleResponse/SessionID")).isEqualTo("base-path-session-001");
        assertThat(NipSoapMessages.xpath(response, "/NESingleResponse/ResponseCode")).isEqualTo("00");

        mockMvc.perform(post("/NIPWS/NIPInterface").contentType("text/xml;charset=UTF-8").content(envelope))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/nibss/NIPWS/NIPTSQInterface?wsdl"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<soap:address location=\"http://localhost/nibss/NIPWS/NIPTSQInterface\"/>")));
        mockMvc.perform(get("/NIPWS/NIPTSQInterface?wsdl"))
                .andExpect(status().isNotFound());
    }

    @Test
    void keepsNibssAdminEndpointsUnderAdminBehindTheAdminToken() throws Exception {
        mockMvc.perform(get("/admin/providers/nibss-nip/keys"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/providers/nibss-nip/keys/simulator-public"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("key"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/admin/providers/nibss-nip/inward/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/providers/nibss-nip/keys").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/nibss/admin/providers/nibss-nip/keys"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/nibss/admin/providers/nibss-nip/keys/simulator-public"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/nibss/admin/providers/nibss-nip/keys/client-public")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("key"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/nibss/admin/providers/nibss-nip/inward/name-enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
