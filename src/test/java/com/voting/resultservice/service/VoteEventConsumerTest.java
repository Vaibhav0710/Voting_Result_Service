package com.voting.resultservice.service;

import com.voting.resultservice.event.VoteCastEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VoteEventConsumer}.
 * <p>
 * Validates Kafka event processing logic including:
 * <ul>
 *   <li>Happy-path delegation to {@link ResultTallyService} and {@link LiveResultService}</li>
 *   <li>Malformed event rejection (null candidateId / electionId)</li>
 *   <li>Correct parameter forwarding to downstream services</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VoteEventConsumerTest {

    @Mock
    private ResultTallyService resultTallyService;

    @Mock
    private LiveResultService liveResultService;

    @InjectMocks
    private VoteEventConsumer voteEventConsumer;

    private static final UUID ELECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CANDIDATE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID VOTE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Test
    @DisplayName("should increment tally and push SSE update on valid event")
    void onVoteCast_shouldIncrementTallyAndPushUpdate() {
        // Arrange
        VoteCastEvent event = buildValidEvent();

        // Act
        voteEventConsumer.onVoteCast(event);

        // Assert
        verify(resultTallyService).incrementVoteCount(ELECTION_ID, CANDIDATE_ID);
        verify(liveResultService).pushUpdate(ELECTION_ID);
    }

    @Test
    @DisplayName("should skip processing when candidateId is null")
    void onVoteCast_withNullCandidateId_shouldSkipProcessing() {
        // Arrange
        VoteCastEvent event = buildValidEvent();
        event.setCandidateId(null);

        // Act
        voteEventConsumer.onVoteCast(event);

        // Assert — no interactions with downstream services
        verifyNoInteractions(resultTallyService);
        verifyNoInteractions(liveResultService);
    }

    @Test
    @DisplayName("should skip processing when electionId is null")
    void onVoteCast_withNullElectionId_shouldSkipProcessing() {
        // Arrange
        VoteCastEvent event = buildValidEvent();
        event.setElectionId(null);

        // Act
        voteEventConsumer.onVoteCast(event);

        // Assert — no interactions with downstream services
        verifyNoInteractions(resultTallyService);
        verifyNoInteractions(liveResultService);
    }

    @Test
    @DisplayName("should forward the exact candidateId and electionId to the tally service")
    void onVoteCast_shouldPassCorrectIds() {
        // Arrange — use unique UUIDs to prove exact forwarding
        UUID specificElection = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID specificCandidate = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        VoteCastEvent event = VoteCastEvent.builder()
                .voteId(VOTE_ID)
                .userId(USER_ID)
                .electionId(specificElection)
                .candidateId(specificCandidate)
                .voteHash("sha256-specific")
                .timestamp(LocalDateTime.now())
                .build();

        // Act
        voteEventConsumer.onVoteCast(event);

        // Assert — exact IDs are forwarded
        verify(resultTallyService).incrementVoteCount(specificElection, specificCandidate);
        verify(liveResultService).pushUpdate(specificElection);
        verifyNoMoreInteractions(resultTallyService, liveResultService);
    }

    // ─── Helper ─────────────────────────────────────────────────────

    private VoteCastEvent buildValidEvent() {
        return VoteCastEvent.builder()
                .voteId(VOTE_ID)
                .userId(USER_ID)
                .electionId(ELECTION_ID)
                .candidateId(CANDIDATE_ID)
                .voteHash("sha256-abc123")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
