package com.edgefort.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulatorOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesProviderSurfacesWithoutSimulatorParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Simulator API"))
                .andExpect(jsonPath("$.paths['/nip/v9.4/nameenquirysingle']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/fundtransfersingleitem_dc']").exists())
                .andExpect(jsonPath("$.paths['/nip/v9.4/txnstatusquerysingleitem']").exists())
                .andExpect(jsonPath("$.paths['/passport/oauth/token']").exists())
                .andExpect(jsonPath("$.paths['/quicktellerservice/api/v5/transactions/TransferFunds']").exists())
                .andExpect(jsonPath("$.paths['/quicktellerservice/api/v5/Transactions']").exists())
                .andExpect(jsonPath("$.paths['/quicktellerservice/api/v5/Transactions'].post.security[0]"
                        + ".InterswitchBearerToken").exists())
                .andExpect(jsonPath("$.paths['/services/airtime/request']").exists())
                .andExpect(jsonPath("$.paths['/services/databundle/request']").exists())
                .andExpect(jsonPath("$.paths['/services/multichoice/request']").exists())
                .andExpect(jsonPath("$.paths['/services/carinsurance/request']").exists())
                .andExpect(jsonPath("$.paths['/services/superagent/transaction/requery']").exists())
                .andExpect(jsonPath("$.paths['/admin/providers']").exists())
                .andExpect(jsonPath("$.paths['/admin/overrides']").exists())
                .andExpect(jsonPath("$.paths['/admin/rules']").exists())
                .andExpect(content().string(not(containsString("X-Simulation-Scenario"))))
                .andExpect(jsonPath("$.components.securitySchemes.AdminToken.name").value("X-Admin-Token"))
                .andExpect(jsonPath("$.components.securitySchemes.InterswitchBearerToken.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.OnafriqApiKey.name").value("x-api-key"));
    }

    @Test
    void exposesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }
}
