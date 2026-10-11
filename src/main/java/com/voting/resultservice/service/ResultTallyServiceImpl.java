package com.voting.resultservice.service;

import com.voting.resultservice.client.CandidateInfo;
import com.voting.resultservice.dto.CandidateResult;
import com.voting.resultservice.dto.ElectionResultResponse;
import com.voting.resultservice.dto.ElectionSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Redis-backed vote tallying service.
 * <p>
 * Data model in Redis:
 * <ul>
 *   <li>{@code res:votes:{electionId}} — Hash: candidateId → voteCount</li>
 *   <li>{@code res:total:{electionId}} — String: total vote count</li>
 *   <li>{@code res:seen:{electionId}} — Set: vote ids already counted</li>
 * </ul>
 * <p>
 * Counting is one atomic Lua script: the vote id is added to the "seen" set and the counters are
 * incremented only if it was not there, so Kafka redeliveries never double-count. Results are
 * enriched with candidate names via {@link CandidateDirectory}, falling back to "Unknown Candidate".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultTallyServiceImpl implements ResultTallyService {

    private static final RedisScript<Long> RECORD_VOTE_SCRIPT = RedisScript.of("""
            if redis.call('SADD', KEYS[1], ARGV[1]) == 1 then
              redis.call('HINCRBY', KEYS[2], ARGV[2], 1)
              redis.call('INCR', KEYS[3])
              return 1
            end
            return 0
            """, Long.class);
    private static final String UNKNOWN_NAME = "Unknown Candidate";
    private static final String UNKNOWN_PARTY = "Unknown";

    private final StringRedisTemplate redisTemplate;
    private final CandidateDirectory candidateDirectory;

    @Value("${result.redis.key-prefix:res:}")
    private String keyPrefix;

    @Override
    public boolean recordVote(UUID electionId, UUID candidateId, UUID voteId) {
        List<String> keys = List.of(seenKey(electionId), votesKey(electionId), totalKey(electionId));
        Long added = redisTemplate.execute(RECORD_VOTE_SCRIPT, keys, voteId.toString(), candidateId.toString());
        boolean counted = Long.valueOf(1L).equals(added);
        if (!counted) {
            log.info("Vote {} already counted for election {}; ignoring redelivery", voteId, electionId);
        }
        return counted;
    }

    @Override
    public ElectionResultResponse getResults(UUID electionId) {
        Map<UUID, Long> voteCounts = fetchVoteCounts(electionId);
        Map<UUID, CandidateInfo> candidates = candidatesFor(electionId, voteCounts);
        List<CandidateResult> results = voteCounts.entrySet().stream()
                .map(entry -> toCandidateResult(entry.getKey(), entry.getValue(), candidates.get(entry.getKey())))
                .sorted(Comparator.comparingLong(CandidateResult::getVoteCount).reversed())
                .toList();
        return ElectionResultResponse.builder()
                .electionId(electionId)
                .totalVotes(fetchTotalVotes(electionId))
                .candidateResults(results)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Override
    public ElectionSummaryResponse getSummary(UUID electionId) {
        Map<UUID, Long> voteCounts = fetchVoteCounts(electionId);
        long totalVotes = fetchTotalVotes(electionId);
        Map<UUID, CandidateInfo> candidates = candidatesFor(electionId, voteCounts);
        List<ElectionSummaryResponse.CandidateSummary> summaries = voteCounts.entrySet().stream()
                .map(entry -> toSummary(entry.getKey(), entry.getValue(), totalVotes, candidates.get(entry.getKey())))
                .sorted(Comparator.comparingLong(ElectionSummaryResponse.CandidateSummary::getVoteCount).reversed())
                .toList();
        return ElectionSummaryResponse.builder()
                .electionId(electionId)
                .totalVotes(totalVotes)
                .candidateSummaries(summaries)
                .winner(summaries.isEmpty() ? null : summaries.get(0))
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private Map<UUID, CandidateInfo> candidatesFor(UUID electionId, Map<UUID, Long> voteCounts) {
        return voteCounts.isEmpty() ? Map.of() : candidateDirectory.candidatesFor(electionId);
    }

    private Map<UUID, Long> fetchVoteCounts(UUID electionId) {
        return redisTemplate.opsForHash().entries(votesKey(electionId)).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> UUID.fromString(entry.getKey().toString()),
                        entry -> Long.parseLong(entry.getValue().toString())));
    }

    private long fetchTotalVotes(UUID electionId) {
        String total = redisTemplate.opsForValue().get(totalKey(electionId));
        return total == null ? 0L : Long.parseLong(total);
    }

    private static CandidateResult toCandidateResult(UUID candidateId, long count, CandidateInfo info) {
        return CandidateResult.builder()
                .candidateId(candidateId)
                .candidateName(info == null ? UNKNOWN_NAME : info.getName())
                .party(info == null ? UNKNOWN_PARTY : info.getParty())
                .voteCount(count)
                .build();
    }

    private static ElectionSummaryResponse.CandidateSummary toSummary(UUID candidateId, long count, long total,
                                                                      CandidateInfo info) {
        double percentage = total > 0 ? (count * 100.0) / total : 0.0;
        return ElectionSummaryResponse.CandidateSummary.builder()
                .candidateId(candidateId)
                .candidateName(info == null ? UNKNOWN_NAME : info.getName())
                .party(info == null ? UNKNOWN_PARTY : info.getParty())
                .voteCount(count)
                .percentage(Math.round(percentage * 100.0) / 100.0)
                .build();
    }

    private String seenKey(UUID electionId) {
        return keyPrefix + "seen:" + electionId;
    }

    private String votesKey(UUID electionId) {
        return keyPrefix + "votes:" + electionId;
    }

    private String totalKey(UUID electionId) {
        return keyPrefix + "total:" + electionId;
    }
}
