package com.voting.resultservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirror DTO of the Voting Service's {@code VoteCastEvent}.
 * <p>
 * Maintained independently (no shared library) to preserve
 * microservice boundary — each service owns its own contracts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteCastEvent {

    private UUID voteId;
    private UUID userId;
    private UUID candidateId;
    private UUID electionId;
    private String voteHash;
    private LocalDateTime timestamp;
}
