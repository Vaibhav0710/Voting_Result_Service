package com.voting.resultservice.service;

import com.voting.resultservice.event.VoteCastEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes {@code vote.cast} events from Kafka and delegates
 * to the tally service for atomic counter increments.
 * <p>
 * Each event triggers:
 * <ol>
 *   <li>Redis counter increment (via {@link ResultTallyService})</li>
 *   <li>SSE push to active subscribers (via {@link LiveResultService})</li>
 * </ol>
 */
@Service
@Slf4j
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
        if (event.getCandidateId() == null || event.getElectionId() == null) {
            log.warn("Received malformed vote.cast event — missing candidateId or electionId: {}", event);
            return;
        }

        log.info("Processing vote.cast: election={}, candidate={}, voter={}",
                event.getElectionId(), event.getCandidateId(), event.getUserId());

        resultTallyService.incrementVoteCount(event.getElectionId(), event.getCandidateId());
        liveResultService.pushUpdate(event.getElectionId());

        log.debug("Vote processed successfully for election={}", event.getElectionId());
    }
}
