package com.voting.resultservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight vote receipt for audit trail display.
 * Mirrors the essential fields from Voting Service's VoteReceiptResponse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteReceipt {

    private UUID voteId;
    private UUID candidateId;
    private UUID electionId;
    private String voteHash;
    private String prevHash;
    private String status;
    private LocalDateTime timestamp;
}
