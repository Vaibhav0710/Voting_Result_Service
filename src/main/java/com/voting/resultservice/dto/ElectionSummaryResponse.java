package com.voting.resultservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Enriched election summary with percentages and winner determination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElectionSummaryResponse {

    private UUID electionId;
    private long totalVotes;
    private List<CandidateSummary> candidateSummaries;
    private CandidateSummary winner;

    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();

    /**
     * Per-candidate summary including vote percentage.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateSummary {

        private UUID candidateId;
        private String candidateName;
        private String party;
        private long voteCount;
        private double percentage;
    }
}
