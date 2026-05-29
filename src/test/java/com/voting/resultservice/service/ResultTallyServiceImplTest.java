package com.voting.resultservice.service;

import com.voting.resultservice.client.CandidateApiResponse;
import com.voting.resultservice.client.CandidateClient;
import com.voting.resultservice.client.CandidateInfo;
import com.voting.resultservice.dto.CandidateResult;
import com.voting.resultservice.dto.ElectionResultResponse;
import com.voting.resultservice.dto.ElectionSummaryResponse;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ResultTallyServiceImpl}.
 * <p>
 * Verifies Redis counter operations, result enrichment via the
 * Candidate Service Feign client, percentage calculations, winner
 * determination, and graceful degradation on downstream failures.
 */
@ExtendWith(MockitoExtension.class)
class ResultTallyServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CandidateClient candidateClient;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ResultTallyServiceImpl resultTallyService;

    private static final String KEY_PREFIX = "res:";
    private static final UUID ELECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CANDIDATE_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CANDIDATE_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resultTallyService, "keyPrefix", KEY_PREFIX);
    }

    // ─── incrementVoteCount ──────────────────────────────────────────

    @Nested
    @DisplayName("incrementVoteCount")
    class IncrementVoteCount {

        @Test
        @DisplayName("should call HINCRBY on the votes hash and INCR on the total key")
        void incrementVoteCount_shouldIncrementRedisHashAndTotal() {
            // Arrange
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String expectedVotesKey = KEY_PREFIX + "votes:" + ELECTION_ID;
            String expectedTotalKey = KEY_PREFIX + "total:" + ELECTION_ID;

            // Act
            resultTallyService.incrementVoteCount(ELECTION_ID, CANDIDATE_A);

            // Assert
            verify(hashOperations).increment(expectedVotesKey, CANDIDATE_A.toString(), 1);
            verify(valueOperations).increment(expectedTotalKey);
            verifyNoMoreInteractions(hashOperations, valueOperations);
        }
    }

    // ─── getResults ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getResults")
    class GetResults {

        @Test
        @DisplayName("should return results from Redis with enriched candidate info")
        void getResults_shouldReturnResultsFromRedis() {
            // Arrange — Redis data
            Map<Object, Object> redisEntries = new LinkedHashMap<>();
            redisEntries.put(CANDIDATE_A.toString(), "60");
            redisEntries.put(CANDIDATE_B.toString(), "40");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(KEY_PREFIX + "votes:" + ELECTION_ID)).thenReturn(redisEntries);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + "total:" + ELECTION_ID)).thenReturn("100");

            // Arrange — Candidate Service response
            CandidateInfo infoA = CandidateInfo.builder()
                    .id(CANDIDATE_A).name("Alice").party("Party X").electionId(ELECTION_ID).build();
            CandidateInfo infoB = CandidateInfo.builder()
                    .id(CANDIDATE_B).name("Bob").party("Party Y").electionId(ELECTION_ID).build();
            CandidateApiResponse<List<CandidateInfo>> apiResponse =
                    new CandidateApiResponse<>(true, "OK", List.of(infoA, infoB));
            when(candidateClient.getActiveCandidates(ELECTION_ID)).thenReturn(apiResponse);

            // Act
            ElectionResultResponse response = resultTallyService.getResults(ELECTION_ID);

            // Assert
            assertThat(response.getElectionId()).isEqualTo(ELECTION_ID);
            assertThat(response.getTotalVotes()).isEqualTo(100L);
            assertThat(response.getCandidateResults()).hasSize(2);

            // Results are sorted descending by voteCount
            CandidateResult first = response.getCandidateResults().get(0);
            assertThat(first.getCandidateId()).isEqualTo(CANDIDATE_A);
            assertThat(first.getCandidateName()).isEqualTo("Alice");
            assertThat(first.getParty()).isEqualTo("Party X");
            assertThat(first.getVoteCount()).isEqualTo(60L);

            CandidateResult second = response.getCandidateResults().get(1);
            assertThat(second.getCandidateId()).isEqualTo(CANDIDATE_B);
            assertThat(second.getCandidateName()).isEqualTo("Bob");
            assertThat(second.getVoteCount()).isEqualTo(40L);
        }

        @Test
        @DisplayName("should return empty results when no votes exist in Redis")
        void getResults_withNoVotes_shouldReturnEmptyResults() {
            // Arrange
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(KEY_PREFIX + "votes:" + ELECTION_ID)).thenReturn(Collections.emptyMap());

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + "total:" + ELECTION_ID)).thenReturn(null);

            // Act
            ElectionResultResponse response = resultTallyService.getResults(ELECTION_ID);

            // Assert
            assertThat(response.getElectionId()).isEqualTo(ELECTION_ID);
            assertThat(response.getTotalVotes()).isEqualTo(0L);
            assertThat(response.getCandidateResults()).isEmpty();

            // Should never call Candidate Service when there are no votes
            verifyNoInteractions(candidateClient);
        }

        @Test
        @DisplayName("should return 'Unknown Candidate' names when Candidate Service is down")
        void getResults_whenCandidateServiceDown_shouldReturnUnknownNames() {
            // Arrange — Redis data
            Map<Object, Object> redisEntries = new LinkedHashMap<>();
            redisEntries.put(CANDIDATE_A.toString(), "25");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(KEY_PREFIX + "votes:" + ELECTION_ID)).thenReturn(redisEntries);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + "total:" + ELECTION_ID)).thenReturn("25");

            // Arrange — Feign throws
            when(candidateClient.getActiveCandidates(ELECTION_ID))
                    .thenThrow(FeignException.ServiceUnavailable.class);

            // Act
            ElectionResultResponse response = resultTallyService.getResults(ELECTION_ID);

            // Assert — graceful degradation
            assertThat(response.getTotalVotes()).isEqualTo(25L);
            assertThat(response.getCandidateResults()).hasSize(1);

            CandidateResult result = response.getCandidateResults().get(0);
            assertThat(result.getCandidateName()).isEqualTo("Unknown Candidate");
            assertThat(result.getParty()).isEqualTo("Unknown");
            assertThat(result.getVoteCount()).isEqualTo(25L);
        }
    }

    // ─── getSummary ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("should calculate vote percentages correctly")
        void getSummary_shouldCalculatePercentagesCorrectly() {
            // Arrange — Redis data: 60 + 40 = 100 total
            Map<Object, Object> redisEntries = new LinkedHashMap<>();
            redisEntries.put(CANDIDATE_A.toString(), "60");
            redisEntries.put(CANDIDATE_B.toString(), "40");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(KEY_PREFIX + "votes:" + ELECTION_ID)).thenReturn(redisEntries);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + "total:" + ELECTION_ID)).thenReturn("100");

            // Arrange — Candidate Service
            CandidateInfo infoA = CandidateInfo.builder()
                    .id(CANDIDATE_A).name("Alice").party("Party X").electionId(ELECTION_ID).build();
            CandidateInfo infoB = CandidateInfo.builder()
                    .id(CANDIDATE_B).name("Bob").party("Party Y").electionId(ELECTION_ID).build();
            when(candidateClient.getActiveCandidates(ELECTION_ID))
                    .thenReturn(new CandidateApiResponse<>(true, "OK", List.of(infoA, infoB)));

            // Act
            ElectionSummaryResponse summary = resultTallyService.getSummary(ELECTION_ID);

            // Assert
            assertThat(summary.getElectionId()).isEqualTo(ELECTION_ID);
            assertThat(summary.getTotalVotes()).isEqualTo(100L);
            assertThat(summary.getCandidateSummaries()).hasSize(2);

            // Summaries are sorted descending by voteCount
            ElectionSummaryResponse.CandidateSummary summaryA = summary.getCandidateSummaries().get(0);
            assertThat(summaryA.getCandidateId()).isEqualTo(CANDIDATE_A);
            assertThat(summaryA.getPercentage()).isEqualTo(60.0);

            ElectionSummaryResponse.CandidateSummary summaryB = summary.getCandidateSummaries().get(1);
            assertThat(summaryB.getCandidateId()).isEqualTo(CANDIDATE_B);
            assertThat(summaryB.getPercentage()).isEqualTo(40.0);
        }

        @Test
        @DisplayName("should determine the candidate with most votes as the winner")
        void getSummary_shouldDetermineWinnerCorrectly() {
            // Arrange — Redis data: Candidate B leads
            Map<Object, Object> redisEntries = new LinkedHashMap<>();
            redisEntries.put(CANDIDATE_A.toString(), "30");
            redisEntries.put(CANDIDATE_B.toString(), "70");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(KEY_PREFIX + "votes:" + ELECTION_ID)).thenReturn(redisEntries);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + "total:" + ELECTION_ID)).thenReturn("100");

            // Arrange — Candidate Service
            CandidateInfo infoA = CandidateInfo.builder()
                    .id(CANDIDATE_A).name("Alice").party("Party X").electionId(ELECTION_ID).build();
            CandidateInfo infoB = CandidateInfo.builder()
                    .id(CANDIDATE_B).name("Bob").party("Party Y").electionId(ELECTION_ID).build();
            when(candidateClient.getActiveCandidates(ELECTION_ID))
                    .thenReturn(new CandidateApiResponse<>(true, "OK", List.of(infoA, infoB)));

            // Act
            ElectionSummaryResponse summary = resultTallyService.getSummary(ELECTION_ID);

            // Assert — Bob is the winner
            assertThat(summary.getWinner()).isNotNull();
            assertThat(summary.getWinner().getCandidateId()).isEqualTo(CANDIDATE_B);
            assertThat(summary.getWinner().getCandidateName()).isEqualTo("Bob");
            assertThat(summary.getWinner().getVoteCount()).isEqualTo(70L);
            assertThat(summary.getWinner().getPercentage()).isEqualTo(70.0);
        }
    }
}
