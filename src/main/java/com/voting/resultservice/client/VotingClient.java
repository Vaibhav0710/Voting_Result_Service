package com.voting.resultservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Feign client for the Voting Service. The election votes endpoint is ADMIN-only, so the caller's
 * JWT is forwarded by {@code FeignConfig}.
 */
@FeignClient(name = "voting-service")
public interface VotingClient {

    @GetMapping("/api/v1/votes/election/{electionId}")
    VotingApiResponse<PageResponse<VoteReceipt>> getElectionVotes(
            @PathVariable("electionId") UUID electionId,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );
}
