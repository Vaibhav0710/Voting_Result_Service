package com.voting.resultservice.controller;

import com.voting.resultservice.dto.AuditTrailResponse;
import com.voting.resultservice.dto.ElectionResultResponse;
import com.voting.resultservice.exception.UpstreamServiceException;
import com.voting.resultservice.security.JwtService;
import com.voting.resultservice.security.SecurityConfig;
import com.voting.resultservice.service.AuditTrailService;
import com.voting.resultservice.service.LiveResultService;
import com.voting.resultservice.service.ResultTallyService;
import com.voting.resultservice.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResultController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=" + TestTokens.SECRET)
class ResultControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ResultTallyService resultTallyService;
    @MockBean private LiveResultService liveResultService;
    @MockBean private AuditTrailService auditTrailService;

    private final UUID electionId = UUID.randomUUID();

    @Test
    void results_arePublic() throws Exception {
        when(resultTallyService.getResults(electionId))
                .thenReturn(ElectionResultResponse.builder().electionId(electionId).candidateResults(List.of()).build());

        mockMvc.perform(get("/api/v1/results/election/{id}", electionId)).andExpect(status().isOk());
    }

    @Test
    void audit_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/v1/results/election/{id}/audit", electionId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void audit_voterRole_403() throws Exception {
        mockMvc.perform(get("/api/v1/results/election/{id}/audit", electionId)
                        .header("Authorization", TestTokens.bearer(UUID.randomUUID(), "ROLE_VOTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void audit_adminRole_200() throws Exception {
        when(auditTrailService.buildAudit(electionId))
                .thenReturn(AuditTrailResponse.builder().electionId(electionId).auditEntries(List.of()).build());

        mockMvc.perform(get("/api/v1/results/election/{id}/audit", electionId)
                        .header("Authorization", TestTokens.bearer(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.electionId").value(electionId.toString()));
    }

    @Test
    void audit_votingServiceDown_503() throws Exception {
        when(auditTrailService.buildAudit(electionId)).thenThrow(new UpstreamServiceException("Voting Service unavailable"));

        mockMvc.perform(get("/api/v1/results/election/{id}/audit", electionId)
                        .header("Authorization", TestTokens.bearer(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable());
    }
}
