package com.voting.resultservice.service;

import com.voting.resultservice.client.CandidateInfo;
import com.voting.resultservice.client.PageResponse;
import com.voting.resultservice.client.VoteReceipt;
import com.voting.resultservice.client.VotingApiResponse;
import com.voting.resultservice.client.VotingClient;
import com.voting.resultservice.dto.AuditTrailResponse;
import com.voting.resultservice.exception.UpstreamServiceException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds an election's audit trail (every vote's hash chain entry) from the Voting Service,
 * which stays the source of truth. Failures surface as 503 instead of an empty, misleading audit.
 */
@Service
@RequiredArgsConstructor
public class AuditTrailService {

    static final int PAGE_SIZE = 500;
    private static final String UNKNOWN_CANDIDATE = "Unknown Candidate";

    private final VotingClient votingClient;
    private final CandidateDirectory candidateDirectory;

    public AuditTrailResponse buildAudit(UUID electionId) {
        List<VoteReceipt> receipts = fetchAllReceipts(electionId);
        Map<UUID, CandidateInfo> candidates = receipts.isEmpty()
                ? Map.of()
                : candidateDirectory.candidatesFor(electionId);
        List<AuditTrailResponse.AuditEntry> entries = receipts.stream()
                .map(receipt -> toEntry(receipt, candidates.get(receipt.getCandidateId())))
                .toList();
        return AuditTrailResponse.builder()
                .electionId(electionId)
                .totalVotes(entries.size())
                .auditEntries(entries)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private List<VoteReceipt> fetchAllReceipts(UUID electionId) {
        List<VoteReceipt> receipts = new ArrayList<>();
        int pageNumber = 0;
        PageResponse<VoteReceipt> page;
        do {
            page = fetchPage(electionId, pageNumber++);
            if (page == null || page.content() == null) {
                break;
            }
            receipts.addAll(page.content());
        } while (!page.isLast());
        return receipts;
    }

    private PageResponse<VoteReceipt> fetchPage(UUID electionId, int pageNumber) {
        try {
            VotingApiResponse<PageResponse<VoteReceipt>> response =
                    votingClient.getElectionVotes(electionId, pageNumber, PAGE_SIZE);
            return response == null ? null : response.getData();
        } catch (FeignException e) {
            throw new UpstreamServiceException("Voting Service unavailable; audit trail cannot be built.", e);
        }
    }

    private static AuditTrailResponse.AuditEntry toEntry(VoteReceipt receipt, CandidateInfo candidate) {
        return AuditTrailResponse.AuditEntry.builder()
                .voteId(receipt.getVoteId())
                .candidateId(receipt.getCandidateId())
                .candidateName(candidate == null ? UNKNOWN_CANDIDATE : candidate.getName())
                .voteHash(receipt.getVoteHash())
                .prevHash(receipt.getPrevHash())
                .status(receipt.getStatus())
                .timestamp(receipt.getTimestamp())
                .build();
    }
}
