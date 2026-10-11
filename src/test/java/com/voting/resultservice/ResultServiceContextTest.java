package com.voting.resultservice;

import com.voting.resultservice.client.CandidateClient;
import com.voting.resultservice.service.CandidateDirectory;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Full context wiring (no Redis/Kafka/Eureka needed) and the Resilience4j proxy on {@link CandidateDirectory}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResultServiceContextTest {

    @Autowired private CandidateDirectory candidateDirectory;
    @MockBean private CandidateClient candidateClient;

    @Test
    void candidateServiceDown_retriedThenEmptyNames() {
        when(candidateClient.getCandidatesByElection(any())).thenThrow(FeignException.ServiceUnavailable.class);

        assertThat(candidateDirectory.candidatesFor(UUID.randomUUID())).isEmpty();
        verify(candidateClient, times(3)).getCandidatesByElection(any());
    }
}
