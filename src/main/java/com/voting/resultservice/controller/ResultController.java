package com.voting.resultservice.controller;

import com.voting.resultservice.dto.ApiResponse;
import com.voting.resultservice.dto.AuditTrailResponse;
import com.voting.resultservice.dto.ElectionResultResponse;
import com.voting.resultservice.dto.ElectionSummaryResponse;
import com.voting.resultservice.service.AuditTrailService;
import com.voting.resultservice.service.LiveResultService;
import com.voting.resultservice.service.ResultTallyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

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
public class ResultController {

    private final ResultTallyService resultTallyService;
    private final LiveResultService liveResultService;
    private final AuditTrailService auditTrailService;

    /**
     * Returns raw vote counts per candidate for the given election.
     */
    @GetMapping("/election/{electionId}")
    public ResponseEntity<ApiResponse<ElectionResultResponse>> getResults(@PathVariable UUID electionId) {
        ElectionResultResponse results = resultTallyService.getResults(electionId);
        return ResponseEntity.ok(ApiResponse.success(results, "Election results retrieved successfully"));
    }

    /**
     * Returns results enriched with percentages and winner determination.
     */
    @GetMapping("/election/{electionId}/summary")
    public ResponseEntity<ApiResponse<ElectionSummaryResponse>> getSummary(@PathVariable UUID electionId) {
        ElectionSummaryResponse summary = resultTallyService.getSummary(electionId);
        return ResponseEntity.ok(ApiResponse.success(summary, "Election summary retrieved successfully"));
    }

    /**
     * Opens an SSE stream for live election result updates.
     * <p>
     * Sends an initial snapshot immediately, then pushes an update each time a new vote is counted.
     *
     * @return SSE event stream with {@code text/event-stream} content type
     */
    @GetMapping(value = "/election/{electionId}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getLiveResults(@PathVariable UUID electionId) {
        return liveResultService.subscribe(electionId);
    }

    /**
     * Returns the full audit trail for an election.
     * <p>
     * Fetches every vote with its hash chain data from the Voting Service (forwarding the caller's JWT)
     * and enriches it with candidate names. 503 if the Voting Service is unavailable.
     * <p>
     * <strong>Requires ADMIN role.</strong>
     */
    @GetMapping("/election/{electionId}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AuditTrailResponse>> getAuditTrail(@PathVariable UUID electionId) {
        AuditTrailResponse auditTrail = auditTrailService.buildAudit(electionId);
        return ResponseEntity.ok(ApiResponse.success(auditTrail, "Audit trail generated successfully"));
    }
}
