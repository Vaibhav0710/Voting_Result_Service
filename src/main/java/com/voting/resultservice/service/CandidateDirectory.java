package com.voting.resultservice.service;

import com.voting.resultservice.client.CandidateApiResponse;
import com.voting.resultservice.client.CandidateClient;
import com.voting.resultservice.client.CandidateInfo;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Candidate names and parties for an election, from the Candidate Service.
 * <p>
 * Protected by a retry and a circuit breaker; when the Candidate Service is down the results are
 * still served, just without names (empty map).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateDirectory {

    static final String CANDIDATE_SERVICE = "candidateService";

    private final CandidateClient candidateClient;

    @Retry(name = CANDIDATE_SERVICE, fallbackMethod = "noCandidates")
    @CircuitBreaker(name = CANDIDATE_SERVICE)
    public Map<UUID, CandidateInfo> candidatesFor(UUID electionId) {
        CandidateApiResponse<List<CandidateInfo>> response = candidateClient.getCandidatesByElection(electionId);
        if (response == null || response.getData() == null) {
            return Map.of();
        }
        return response.getData().stream()
                .collect(Collectors.toMap(CandidateInfo::getId, Function.identity(), (first, second) -> first));
    }

    Map<UUID, CandidateInfo> noCandidates(UUID electionId, Throwable cause) {
        log.warn("Candidate names unavailable for election {}: {}", electionId, cause.toString());
        return Map.of();
    }
}
