package com.edgefort.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulatorOpenApiIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void usesTheSimulatorApplicationName() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("simulator");
    }

    @Test
    void exposesProviderAndAdminOperationsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Simulator API"))
                .andExpect(jsonPath("$.paths['/nip/v9.4/nameenquirysingle']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/fundtransfersingleitem_dc']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/fundtransfersingleitem_dd']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/txnstatusquerysingleitem']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/balanceenquiry']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/fundtransferAdvice_dc']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/fundtransferAdvice_dd']").exists())
                .andExpect(jsonPath("$.paths['/passport/oauth/token']").exists())
                .andExpect(jsonPath("$.paths['/quicktellerservice/api/v5/transactions/DoAccountNameInquiry']").exists())
                .andExpect(jsonPath("$.paths['/quicktellerservice/api/v5/configuration/fundstransferbanks']").exists())
                .andExpect(jsonPath("$.paths['/quicktellerservice/api/v5/transactions/Transfer']").exists())
                .andExpect(jsonPath("$.paths['/quicktellerservice/api/v5/Transactions']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/fundtransfersingleitem_dc'].post.parameters[0].name")
                        .value("X-Simulation-Scenario"))
                .andExpect(jsonPath("$.paths['/nip/v9.4/fundtransfersingleitem_dc'].post.parameters[0].schema.default")
                        .value("success"))
                .andExpect(content().string(containsString(
                        "\"enum\":[\"success\",\"failure\",\"pending\",\"delayed-success\"," +
                                "\"timeout\",\"event-timeout\",\"late-event\"]"
                )))
                .andExpect(jsonPath("$.paths['/admin/providers']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.AdminToken.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.AdminToken.name").value("X-Admin-Token"))
                .andExpect(jsonPath("$.components.securitySchemes.InterswitchBearerToken.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.InterswitchBearerToken.scheme").value("bearer"));
    }

    @Test
    void exposesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }
}