package com.voting.resultservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for the Voting Service.
 * <p>
 * Used exclusively for the admin audit trail endpoint —
 * fetches raw vote records with hash chain data.
 */
@FeignClient(name = "voting-service")
public interface VotingClient {

    /**
     * Fetches all votes for an election (paginated).
     * Requires ADMIN authentication forwarded via request headers.
     */
    @GetMapping("/api/v1/votes/election/{electionId}")
    VotingApiResponse<List<VoteReceipt>> getElectionVotes(
            @PathVariable("electionId") UUID electionId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size
    );
}
