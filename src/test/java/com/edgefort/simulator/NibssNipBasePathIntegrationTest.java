package com.edgefort.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest(properties = "simulator.providers.nibss-nip.base-path=/nibss")
@AutoConfigureMockMvc
class NibssNipBasePathIntegrationTest {

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
}
