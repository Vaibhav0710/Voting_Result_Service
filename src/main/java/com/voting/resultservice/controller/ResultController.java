package com.voting.resultservice.controller;

import com.voting.resultservice.client.*;
import com.voting.resultservice.dto.*;
import com.voting.resultservice.service.LiveResultService;
import com.voting.resultservice.service.ResultTallyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for election results, live updates, and audit trail.
 * <p>
 * Endpoint visibility:
 * <ul>
 *   <li><strong>Public:</strong> results, summary, live SSE</li>
 *   <li><strong>Admin only:</strong> audit trail</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
@Slf4j
public class ResultController {

    private final ResultTallyService resultTallyService;
    private final LiveResultService liveResultService;
    private final VotingClient votingClient;
    private final CandidateClient candidateClient;

    /**
     * Returns raw vote counts per candidate for the given election.
     */
    @GetMapping("/election/{electionId}")
    public ResponseEntity<ApiResponse<ElectionResultResponse>> getResults(
            @PathVariable UUID electionId) {
        log.info("GET /results/election/{} — fetching results", electionId);

        ElectionResultResponse results = resultTallyService.getResults(electionId);

        return ResponseEntity.ok(ApiResponse.success(results, "Election results retrieved successfully"));
    }

    /**
     * Returns results enriched with percentages and winner determination.
     */
    @GetMapping("/election/{electionId}/summary")
    public ResponseEntity<ApiResponse<ElectionSummaryResponse>> getSummary(
            @PathVariable UUID electionId) {
        log.info("GET /results/election/{}/summary — fetching summary", electionId);

        ElectionSummaryResponse summary = resultTallyService.getSummary(electionId);

        return ResponseEntity.ok(ApiResponse.success(summary, "Election summary retrieved successfully"));
    }

    /**
     * Opens an SSE stream for live election result updates.
     * <p>
     * Sends an initial snapshot immediately, then pushes incremental
     * updates each time a new vote is processed.
     *
     * @return SSE event stream with {@code text/event-stream} content type
     */
    @GetMapping(value = "/election/{electionId}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getLiveResults(@PathVariable UUID electionId) {
        log.info("GET /results/election/{}/live — new SSE subscriber", electionId);
        return liveResultService.subscribe(electionId);
    }

    /**
     * Returns the full audit trail for an election.
     * <p>
     * Fetches raw vote data (with hashes and chain links) from the Voting Service
     * and enriches with candidate names from the Candidate Service.
     * <p>
     * <strong>Requires ADMIN role.</strong>
     */
    @GetMapping("/election/{electionId}/audit")
    public ResponseEntity<ApiResponse<AuditTrailResponse>> getAuditTrail(
            @PathVariable UUID electionId) {
        log.info("GET /results/election/{}/audit — generating audit trail", electionId);

        List<AuditTrailResponse.AuditEntry> auditEntries = fetchAuditEntries(electionId);

        AuditTrailResponse auditTrail = AuditTrailResponse.builder()
                .electionId(electionId)
                .totalVotes(auditEntries.size())
                .auditEntries(auditEntries)
                .generatedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(ApiResponse.success(auditTrail, "Audit trail generated successfully"));
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    /**
     * Fetches votes from Voting Service and enriches with candidate names.
     */
    private List<AuditTrailResponse.AuditEntry> fetchAuditEntries(UUID electionId) {
        try {
            VotingApiResponse<List<VoteReceipt>> votingResponse =
                    votingClient.getElectionVotes(electionId, 0, 1000);

            if (votingResponse == null || votingResponse.getData() == null) {
                return Collections.emptyList();
            }

            // Build candidate name lookup
            var candidateNames = fetchCandidateNames(electionId);

            return votingResponse.getData().stream()
                    .map(receipt -> AuditTrailResponse.AuditEntry.builder()
                            .voteId(receipt.getVoteId())
                            .candidateId(receipt.getCandidateId())
                            .candidateName(candidateNames.getOrDefault(
                                    receipt.getCandidateId(), "Unknown Candidate"))
                            .voteHash(receipt.getVoteHash())
                            .prevHash(receipt.getPrevHash())
                            .status(receipt.getStatus())
                            .timestamp(receipt.getTimestamp())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch audit trail for election={}: {}", electionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private java.util.Map<UUID, String> fetchCandidateNames(UUID electionId) {
        try {
            CandidateApiResponse<List<CandidateInfo>> response =
                    candidateClient.getActiveCandidates(electionId);

            if (response != null && response.getData() != null) {
                return response.getData().stream()
                        .collect(Collectors.toMap(CandidateInfo::getId, CandidateInfo::getName));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch candidate names for audit: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
