package com.voting.resultservice.service;

import com.voting.resultservice.dto.ElectionResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Server-Sent Event (SSE) connections for live election results.
 * <p>
 * When a new vote is processed by {@link VoteEventConsumer}, this service
 * pushes updated results to all active subscribers for that election.
 * <p>
 * Thread-safe: uses {@link ConcurrentHashMap} for election→emitters mapping
 * and {@link CopyOnWriteArrayList} for per-election emitter lists.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LiveResultService {

    private final ResultTallyService resultTallyService;

    private final Map<UUID, List<SseEmitter>> emittersByElection = new ConcurrentHashMap<>();

    @Value("${result.redis.sse-timeout:300000}")
    private long sseTimeout;

    /**
     * Creates a new SSE emitter for the given election and registers cleanup callbacks.
     *
     * @param electionId the election to subscribe to
     * @return a configured {@link SseEmitter} that will receive live result updates
     */
    public SseEmitter subscribe(UUID electionId) {
        SseEmitter emitter = new SseEmitter(sseTimeout);
        List<SseEmitter> emitters = emittersByElection.computeIfAbsent(
                electionId, k -> new CopyOnWriteArrayList<>());

        emitters.add(emitter);
        log.info("New SSE subscriber for election={}, total subscribers={}", electionId, emitters.size());

        // Cleanup on disconnect, timeout, or error
        Runnable removeEmitter = () -> {
            emitters.remove(emitter);
            log.debug("SSE subscriber removed for election={}, remaining={}", electionId, emitters.size());
        };
        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(e -> removeEmitter.run());

        // Send initial snapshot so the client has current state immediately
        sendInitialSnapshot(emitter, electionId);

        return emitter;
    }

    /**
     * Pushes updated results to all active SSE subscribers for the given election.
     * Called by {@link VoteEventConsumer} after each vote is tallied.
     */
    public void pushUpdate(UUID electionId) {
        List<SseEmitter> emitters = emittersByElection.get(electionId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        ElectionResultResponse results = resultTallyService.getResults(electionId);
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("result-update")
                        .data(results));
            } catch (IOException e) {
                log.debug("Failed to send SSE event, removing dead emitter for election={}", electionId);
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }

    private void sendInitialSnapshot(SseEmitter emitter, UUID electionId) {
        try {
            ElectionResultResponse results = resultTallyService.getResults(electionId);
            emitter.send(SseEmitter.event()
                    .name("initial-results")
                    .data(results));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE snapshot for election={}", electionId);
        }
    }
}
