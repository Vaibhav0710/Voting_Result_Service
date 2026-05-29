package com.voting.resultservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Lightweight candidate representation for enriching election results.
 * <p>
 * Only carries display-relevant fields — no status or audit metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateInfo {

    private UUID id;
    private String name;
    private String party;
    private UUID electionId;
}
