package com.voting.resultservice.service;

import com.voting.resultservice.dto.ElectionResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LiveResultService}.
 * <p>
 * Validates SSE subscription management, safe handling of missing
 * subscribers, and event broadcasting to active emitters.
 */
@ExtendWith(MockitoExtension.class)
class LiveResultServiceTest {

    @Mock
    private ResultTallyService resultTallyService;

    @InjectMocks
    private LiveResultService liveResultService;

    private static final UUID ELECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(liveResultService, "sseTimeout", 300_000L);
    }

    @Test
    @DisplayName("subscribe should return a non-null SseEmitter")
    void subscribe_shouldReturnSseEmitter() {
        // Arrange — mock the initial snapshot fetch
        ElectionResultResponse mockResults = ElectionResultResponse.builder()
                .electionId(ELECTION_ID)
                .totalVotes(0L)
                .candidateResults(Collections.emptyList())
                .lastUpdated(LocalDateTime.now())
                .build();
        when(resultTallyService.getResults(ELECTION_ID)).thenReturn(mockResults);

        // Act
        SseEmitter emitter = liveResultService.subscribe(ELECTION_ID);

        // Assert
        assertThat(emitter).isNotNull();
        verify(resultTallyService).getResults(ELECTION_ID);
    }

    @Test
    @DisplayName("pushUpdate with no subscribers should not throw any exception")
    void pushUpdate_withNoSubscribers_shouldNotThrow() {
        // Act & Assert — no subscribers exist for this election
        assertThatCode(() -> liveResultService.pushUpdate(ELECTION_ID))
                .doesNotThrowAnyException();

        // The tally service should NOT be called since there are no subscribers
        verifyNoInteractions(resultTallyService);
    }

    @Test
    @DisplayName("pushUpdate with active subscribers should complete without error")
    void pushUpdate_withSubscribers_shouldSendEvent() {
        // Arrange — first subscribe to create an emitter
        ElectionResultResponse initialResults = ElectionResultResponse.builder()
                .electionId(ELECTION_ID)
                .totalVotes(0L)
                .candidateResults(Collections.emptyList())
                .lastUpdated(LocalDateTime.now())
                .build();
        when(resultTallyService.getResults(ELECTION_ID)).thenReturn(initialResults);

        liveResultService.subscribe(ELECTION_ID);

        // Arrange — updated results for the push
        ElectionResultResponse updatedResults = ElectionResultResponse.builder()
                .electionId(ELECTION_ID)
                .totalVotes(5L)
                .candidateResults(Collections.emptyList())
                .lastUpdated(LocalDateTime.now())
                .build();
        when(resultTallyService.getResults(ELECTION_ID)).thenReturn(updatedResults);

        // Act & Assert — should not throw
        assertThatCode(() -> liveResultService.pushUpdate(ELECTION_ID))
                .doesNotThrowAnyException();

        // getResults called twice: once for initial snapshot, once for the push
        verify(resultTallyService, times(2)).getResults(ELECTION_ID);
    }
}
