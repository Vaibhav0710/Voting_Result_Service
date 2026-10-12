package com.voting.resultservice.service;

import com.voting.resultservice.audit.AuditLogger;
import com.voting.resultservice.client.CandidateInfo;
import com.voting.resultservice.client.PageResponse;
import com.voting.resultservice.client.VoteReceipt;
import com.voting.resultservice.client.VotingApiResponse;
import com.voting.resultservice.client.VotingClient;
import com.voting.resultservice.dto.AuditTrailResponse;
import com.voting.resultservice.exception.UpstreamServiceException;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailServiceTest {

    @Mock private VotingClient votingClient;
    @Mock private CandidateDirectory candidateDirectory;
    @Mock private AuditLogger auditLogger;
    @InjectMocks private AuditTrailService auditTrailService;

    private final UUID electionId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();

    private VoteReceipt receipt(String hash) {
        return VoteReceipt.builder().voteId(UUID.randomUUID()).candidateId(candidateId)
                .electionId(electionId).voteHash(hash).status("CAST").build();
    }

    private VotingApiResponse<PageResponse<VoteReceipt>> page(int number, int totalPages, VoteReceipt... receipts) {
        PageResponse<VoteReceipt> page = new PageResponse<>(List.of(receipts),
                new PageResponse.PageMetadata(AuditTrailService.PAGE_SIZE, number, 3, totalPages));
        return new VotingApiResponse<>(true, "ok", page);
    }

    @Test
    void buildAudit_readsAllPagesAndNamesCandidates() {
        when(votingClient.getElectionVotes(electionId, 0, AuditTrailService.PAGE_SIZE))
                .thenReturn(page(0, 2, receipt("h1"), receipt("h2")));
        when(votingClient.getElectionVotes(electionId, 1, AuditTrailService.PAGE_SIZE))
                .thenReturn(page(1, 2, receipt("h3")));
        when(candidateDirectory.candidatesFor(electionId))
                .thenReturn(Map.of(candidateId, CandidateInfo.builder().id(candidateId).name("Alice").build()));

        AuditTrailResponse audit = auditTrailService.buildAudit(electionId);

        assertThat(audit.getTotalVotes()).isEqualTo(3);
        assertThat(audit.getAuditEntries()).extracting(AuditTrailResponse.AuditEntry::getVoteHash)
                .containsExactly("h1", "h2", "h3");
        assertThat(audit.getAuditEntries().get(0).getCandidateName()).isEqualTo("Alice");
        verify(auditLogger).recordForCurrentUser("AUDIT_TRAIL_VIEWED", "election:" + electionId, "SUCCESS");
    }

    @Test
    void votingServiceFailure_throwsUpstreamError_notEmptyAudit() {
        when(votingClient.getElectionVotes(electionId, 0, AuditTrailService.PAGE_SIZE))
                .thenThrow(FeignException.ServiceUnavailable.class);

        assertThatThrownBy(() -> auditTrailService.buildAudit(electionId))
                .isInstanceOf(UpstreamServiceException.class);
    }
}
