package com.voting.resultservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for the Candidate Service. Uses the "all candidates of an election" endpoint so that
 * votes for candidates who were later disqualified or withdrew still show a name.
 */
@FeignClient(name = "candidate-service")
public interface CandidateClient {

    @GetMapping("/api/v1/candidates/election/{electionId}")
    CandidateApiResponse<List<CandidateInfo>> getCandidatesByElection(@PathVariable("electionId") UUID electionId);
}
