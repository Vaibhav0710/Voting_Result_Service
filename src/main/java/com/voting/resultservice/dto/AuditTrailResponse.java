package com.voting.resultservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full audit trail for an election, fetched from the Voting Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailResponse {

    private UUID electionId;
    private long totalVotes;
    private List<AuditEntry> auditEntries;

    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();

    /**
     * Individual audit entry representing a single vote's integrity data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEntry {

        private UUID voteId;
        private UUID candidateId;
        private String candidateName;
        private String voteHash;
        private String prevHash;
        private String status;
        private LocalDateTime timestamp;
    }
}
