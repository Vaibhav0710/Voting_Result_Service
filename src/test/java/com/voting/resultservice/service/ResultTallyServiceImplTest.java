package com.voting.resultservice.service;

import com.voting.resultservice.client.CandidateInfo;
import com.voting.resultservice.dto.CandidateResult;
import com.voting.resultservice.dto.ElectionResultResponse;
import com.voting.resultservice.dto.ElectionSummaryResponse;
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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResultTallyServiceImpl}: idempotent Redis counting, enrichment with
 * candidate names, percentages and winner.
 */
@ExtendWith(MockitoExtension.class)
class ResultTallyServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private CandidateDirectory candidateDirectory;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private ResultTallyServiceImpl resultTallyService;

    private static final String KEY_PREFIX = "res:";
    private static final UUID ELECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CANDIDATE_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CANDIDATE_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID VOTE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resultTallyService, "keyPrefix", KEY_PREFIX);
    }

    private void redisHas(Map<Object, Object> votes, String total) {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(KEY_PREFIX + "votes:" + ELECTION_ID)).thenReturn(votes);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY_PREFIX + "total:" + ELECTION_ID)).thenReturn(total);
    }

    private static Map<Object, Object> votes(String countA, String countB) {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put(CANDIDATE_A.toString(), countA);
        if (countB != null) {
            entries.put(CANDIDATE_B.toString(), countB);
        }
        return entries;
    }

    private static Map<UUID, CandidateInfo> aliceAndBob() {
        return Map.of(
                CANDIDATE_A, CandidateInfo.builder().id(CANDIDATE_A).name("Alice").party("Party X").build(),
                CANDIDATE_B, CandidateInfo.builder().id(CANDIDATE_B).name("Bob").party("Party Y").build());
    }

    @Nested
    @DisplayName("recordVote")
    class RecordVote {

        @Test
        @DisplayName("counts a vote the first time its voteId is seen")
        void firstDelivery_isCounted() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

            boolean counted = resultTallyService.recordVote(ELECTION_ID, CANDIDATE_A, VOTE_ID);

            assertThat(counted).isTrue();
            verify(redisTemplate).execute(any(RedisScript.class),
                    eq(List.of(KEY_PREFIX + "seen:" + ELECTION_ID, KEY_PREFIX + "votes:" + ELECTION_ID,
                            KEY_PREFIX + "total:" + ELECTION_ID)),
                    eq(VOTE_ID.toString()), eq(CANDIDATE_A.toString()));
        }

        @Test
        @DisplayName("ignores a redelivered vote (same voteId twice counts once)")
        void sameVoteIdTwice_countsOnce() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L, 0L);

            assertThat(resultTallyService.recordVote(ELECTION_ID, CANDIDATE_A, VOTE_ID)).isTrue();
            assertThat(resultTallyService.recordVote(ELECTION_ID, CANDIDATE_A, VOTE_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("getResults")
    class GetResults {

        @Test
        @DisplayName("returns Redis counts enriched with candidate names, highest first")
        void getResults_shouldReturnResultsFromRedis() {
            redisHas(votes("60", "40"), "100");
            when(candidateDirectory.candidatesFor(ELECTION_ID)).thenReturn(aliceAndBob());

            ElectionResultResponse response = resultTallyService.getResults(ELECTION_ID);

            assertThat(response.getTotalVotes()).isEqualTo(100L);
            CandidateResult first = response.getCandidateResults().get(0);
            assertThat(first.getCandidateName()).isEqualTo("Alice");
            assertThat(first.getParty()).isEqualTo("Party X");
            assertThat(first.getVoteCount()).isEqualTo(60L);
            assertThat(response.getCandidateResults().get(1).getCandidateName()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("returns empty results without calling Candidate Service when no votes exist")
        void getResults_withNoVotes_shouldReturnEmptyResults() {
            redisHas(Collections.emptyMap(), null);

            ElectionResultResponse response = resultTallyService.getResults(ELECTION_ID);

            assertThat(response.getTotalVotes()).isZero();
            assertThat(response.getCandidateResults()).isEmpty();
            verifyNoInteractions(candidateDirectory);
        }

        @Test
        @DisplayName("falls back to 'Unknown Candidate' when names are unavailable")
        void getResults_whenNamesUnavailable_shouldReturnUnknownNames() {
            redisHas(votes("25", null), "25");
            when(candidateDirectory.candidatesFor(ELECTION_ID)).thenReturn(Map.of());

            CandidateResult result = resultTallyService.getResults(ELECTION_ID).getCandidateResults().get(0);

            assertThat(result.getCandidateName()).isEqualTo("Unknown Candidate");
            assertThat(result.getParty()).isEqualTo("Unknown");
            assertThat(result.getVoteCount()).isEqualTo(25L);
        }
    }

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("calculates vote percentages")
        void getSummary_shouldCalculatePercentagesCorrectly() {
            redisHas(votes("60", "40"), "100");
            when(candidateDirectory.candidatesFor(ELECTION_ID)).thenReturn(aliceAndBob());

            ElectionSummaryResponse summary = resultTallyService.getSummary(ELECTION_ID);

            assertThat(summary.getCandidateSummaries().get(0).getPercentage()).isEqualTo(60.0);
            assertThat(summary.getCandidateSummaries().get(1).getPercentage()).isEqualTo(40.0);
        }

        @Test
        @DisplayName("picks the candidate with most votes as winner")
        void getSummary_shouldDetermineWinnerCorrectly() {
            redisHas(votes("30", "70"), "100");
            when(candidateDirectory.candidatesFor(ELECTION_ID)).thenReturn(aliceAndBob());

            ElectionSummaryResponse summary = resultTallyService.getSummary(ELECTION_ID);

            assertThat(summary.getWinner().getCandidateName()).isEqualTo("Bob");
            assertThat(summary.getWinner().getPercentage()).isEqualTo(70.0);
        }
    }
}
