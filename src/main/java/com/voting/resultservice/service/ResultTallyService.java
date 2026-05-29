package com.voting.resultservice.service;

import com.voting.resultservice.dto.ElectionResultResponse;
import com.voting.resultservice.dto.ElectionSummaryResponse;

import java.util.UUID;

/**
 * Contract for election result tallying operations.
 * <p>
 * Implementations must ensure atomicity of counter increments
 * for correctness under concurrent Kafka consumer processing.
 */
public interface ResultTallyService {

    /**
     * Atomically increments the vote count for a candidate within an election.
     */
    void incrementVoteCount(UUID electionId, UUID candidateId);

    /**
     * Retrieves raw vote counts per candidate for the given election.
     */
    ElectionResultResponse getResults(UUID electionId);

    /**
     * Retrieves results enriched with percentages and winner determination.
     */
    ElectionSummaryResponse getSummary(UUID electionId);
}
