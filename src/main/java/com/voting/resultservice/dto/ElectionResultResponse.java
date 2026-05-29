package com.voting.resultservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Raw election results — vote counts per candidate without percentages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElectionResultResponse {

    private UUID electionId;
    private long totalVotes;
    private List<CandidateResult> candidateResults;

    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
