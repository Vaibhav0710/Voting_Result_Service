package com.voting.resultservice.service;

import com.voting.resultservice.client.CandidateApiResponse;
import com.voting.resultservice.client.CandidateClient;
import com.voting.resultservice.client.CandidateInfo;
import com.voting.resultservice.dto.CandidateResult;
import com.voting.resultservice.dto.ElectionResultResponse;
import com.voting.resultservice.dto.ElectionSummaryResponse;
import com.voting.resultservice.exception.ElectionNotFoundException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-backed vote tallying service using atomic counter operations.
 * <p>
 * Data model in Redis:
 * <ul>
 *   <li>{@code res:votes:{electionId}} — Hash: candidateId → voteCount</li>
 *   <li>{@code res:total:{electionId}} — String: total vote count</li>
 * </ul>
 * <p>
 * All increment operations are atomic ({@code HINCRBY}, {@code INCR})
 * ensuring correctness under concurrent Kafka consumer processing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResultTallyServiceImpl implements ResultTallyService {

    private final StringRedisTemplate redisTemplate;
    private final CandidateClient candidateClient;

    @Value("${result.redis.key-prefix:res:}")
    private String keyPrefix;

    @Override
    public void incrementVoteCount(UUID electionId, UUID candidateId) {
        String votesKey = buildVotesKey(electionId);
        String totalKey = buildTotalKey(electionId);

        redisTemplate.opsForHash().increment(votesKey, candidateId.toString(), 1);
        redisTemplate.opsForValue().increment(totalKey);

        log.debug("Incremented vote count: election={}, candidate={}", electionId, candidateId);
    }

    @Override
    public ElectionResultResponse getResults(UUID electionId) {
        Map<UUID, Long> voteCounts = fetchVoteCounts(electionId);
        long totalVotes = fetchTotalVotes(electionId);

        List<CandidateResult> candidateResults = enrichWithCandidateInfo(voteCounts, electionId);

        return ElectionResultResponse.builder()
                .electionId(electionId)
                .totalVotes(totalVotes)
                .candidateResults(candidateResults)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Override
    public ElectionSummaryResponse getSummary(UUID electionId) {
        Map<UUID, Long> voteCounts = fetchVoteCounts(electionId);
        long totalVotes = fetchTotalVotes(electionId);

        List<ElectionSummaryResponse.CandidateSummary> summaries =
                buildCandidateSummaries(voteCounts, totalVotes, electionId);

        ElectionSummaryResponse.CandidateSummary winner = determineWinner(summaries);

        return ElectionSummaryResponse.builder()
                .electionId(electionId)
                .totalVotes(totalVotes)
                .candidateSummaries(summaries)
                .winner(winner)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    private Map<UUID, Long> fetchVoteCounts(UUID electionId) {
        String votesKey = buildVotesKey(electionId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(votesKey);

        if (entries.isEmpty()) {
            log.info("No votes found in Redis for election={}", electionId);
            return Collections.emptyMap();
        }

        return entries.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> UUID.fromString(entry.getKey().toString()),
                        entry -> Long.parseLong(entry.getValue().toString())
                ));
    }

    private long fetchTotalVotes(UUID electionId) {
        String totalKey = buildTotalKey(electionId);
        String total = redisTemplate.opsForValue().get(totalKey);
        return total != null ? Long.parseLong(total) : 0L;
    }

    /**
     * Enriches raw vote counts with candidate names and parties via Feign.
     * Falls back to "Unknown Candidate" if the Candidate Service is unreachable.
     */
    private List<CandidateResult> enrichWithCandidateInfo(Map<UUID, Long> voteCounts, UUID electionId) {
        Map<UUID, CandidateInfo> candidateCache = fetchCandidateCache(electionId);

        return voteCounts.entrySet().stream()
                .map(entry -> {
                    UUID candidateId = entry.getKey();
                    CandidateInfo info = candidateCache.get(candidateId);

                    return CandidateResult.builder()
                            .candidateId(candidateId)
                            .candidateName(info != null ? info.getName() : "Unknown Candidate")
                            .party(info != null ? info.getParty() : "Unknown")
                            .voteCount(entry.getValue())
                            .build();
                })
                .sorted(Comparator.comparingLong(CandidateResult::getVoteCount).reversed())
                .collect(Collectors.toList());
    }

    private List<ElectionSummaryResponse.CandidateSummary> buildCandidateSummaries(
            Map<UUID, Long> voteCounts, long totalVotes, UUID electionId) {

        Map<UUID, CandidateInfo> candidateCache = fetchCandidateCache(electionId);

        return voteCounts.entrySet().stream()
                .map(entry -> {
                    UUID candidateId = entry.getKey();
                    long count = entry.getValue();
                    CandidateInfo info = candidateCache.get(candidateId);
                    double percentage = totalVotes > 0 ? (count * 100.0) / totalVotes : 0.0;

                    return ElectionSummaryResponse.CandidateSummary.builder()
                            .candidateId(candidateId)
                            .candidateName(info != null ? info.getName() : "Unknown Candidate")
                            .party(info != null ? info.getParty() : "Unknown")
                            .voteCount(count)
                            .percentage(Math.round(percentage * 100.0) / 100.0)
                            .build();
                })
                .sorted(Comparator.comparingLong(ElectionSummaryResponse.CandidateSummary::getVoteCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Fetches candidate info from the Candidate Service via Feign.
     * Returns an empty map (graceful degradation) if the service is unavailable.
     */
    private Map<UUID, CandidateInfo> fetchCandidateCache(UUID electionId) {
        try {
            CandidateApiResponse<List<CandidateInfo>> response =
                    candidateClient.getActiveCandidates(electionId);

            if (response != null && response.getData() != null) {
                return response.getData().stream()
                        .collect(Collectors.toMap(CandidateInfo::getId, info -> info));
            }
        } catch (FeignException e) {
            log.warn("Failed to fetch candidates for election={}: {}. Results will use IDs only.",
                    electionId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    private ElectionSummaryResponse.CandidateSummary determineWinner(
            List<ElectionSummaryResponse.CandidateSummary> summaries) {
        return summaries.stream()
                .max(Comparator.comparingLong(ElectionSummaryResponse.CandidateSummary::getVoteCount))
                .orElse(null);
    }

    private String buildVotesKey(UUID electionId) {
        return keyPrefix + "votes:" + electionId;
    }

    private String buildTotalKey(UUID electionId) {
        return keyPrefix + "total:" + electionId;
    }
}
