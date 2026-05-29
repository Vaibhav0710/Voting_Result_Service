package com.voting.resultservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Per-candidate result within an election.
 * Enriched with candidate name/party from the Candidate Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateResult {

    private UUID candidateId;
    private String candidateName;
    private String party;
    private long voteCount;
}
