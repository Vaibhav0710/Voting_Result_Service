package com.voting.resultservice.service;

import com.voting.resultservice.dto.ElectionResultResponse;
import com.voting.resultservice.dto.ElectionSummaryResponse;

import java.util.UUID;

/**
 * Contract for election result tallying operations.
 * <p>
 * Implementations must count each vote exactly once, atomically, even when Kafka
 * redelivers the same event or several consumers process events concurrently.
 */
public interface ResultTallyService {

    /**
     * Atomically counts a vote for a candidate within an election, once per vote id.
     *
     * @return {@code false} when this vote id was already counted (Kafka redelivery)
     */
    boolean recordVote(UUID electionId, UUID candidateId, UUID voteId);

    /**
     * Retrieves raw vote counts per candidate for the given election.
     */
    ElectionResultResponse getResults(UUID electionId);

    /**
     * Retrieves results enriched with percentages and winner determination.
     */
    ElectionSummaryResponse getSummary(UUID electionId);
}
