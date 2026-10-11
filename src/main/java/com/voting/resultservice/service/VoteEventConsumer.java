package com.voting.resultservice.service;

import com.voting.resultservice.event.VoteCastEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes {@code vote.cast} events from Kafka.
 * <p>
 * Each event triggers:
 * <ol>
 *   <li>An idempotent Redis count (via {@link ResultTallyService}), keyed by vote id</li>
 *   <li>An SSE push to active subscribers (via {@link LiveResultService}), only if the vote was newly counted</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoteEventConsumer {

    private final ResultTallyService resultTallyService;
    private final LiveResultService liveResultService;

    @KafkaListener(
            topics = "vote.cast",
            groupId = "result-service-group",
            containerFactory = "voteCastListenerFactory"
    )
    public void onVoteCast(VoteCastEvent event) {
        if (event.getVoteId() == null || event.getCandidateId() == null || event.getElectionId() == null) {
            log.warn("Ignoring malformed vote.cast event (voteId, candidateId and electionId are required): {}", event);
            return;
        }
        boolean counted = resultTallyService.recordVote(event.getElectionId(), event.getCandidateId(), event.getVoteId());
        if (counted) {
            liveResultService.pushUpdate(event.getElectionId());
        }
    }
}
