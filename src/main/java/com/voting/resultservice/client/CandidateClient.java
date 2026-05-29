package com.voting.resultservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for the Candidate Service.
 * <p>
 * Used to enrich election results with candidate names and party information.
 * Calls are made via Eureka service discovery (no hardcoded URLs).
 * <p>
 * <strong>Note:</strong> The Candidate Service wraps responses in {@code ApiResponse<T>}.
 * Feign returns are typed accordingly using {@link CandidateApiResponse}.
 */
@FeignClient(name = "candidate-service")
public interface CandidateClient {

    /**
     * Fetches a single candidate's display information.
     *
     * @return wrapped response containing candidate data
     */
    @GetMapping("/api/v1/candidates/{id}")
    CandidateApiResponse<CandidateInfo> getCandidate(@PathVariable("id") UUID candidateId);

    /**
     * Fetches all active candidates for an election.
     * Used for building complete result sets even if some candidates have zero votes.
     *
     * @return wrapped response containing list of active candidates
     */
    @GetMapping("/api/v1/candidates/election/{electionId}/active")
    CandidateApiResponse<List<CandidateInfo>> getActiveCandidates(@PathVariable("electionId") UUID electionId);
}
