package com.voting.resultservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Result Service — Stateless real-time vote aggregation.
 * <p>
 * Consumes {@code vote.cast} Kafka events, maintains Redis-backed counters,
 * and exposes REST + SSE endpoints for live election results.
 * <p>
 * <strong>No database.</strong> Source of truth is the Voting Service DB;
 * this service is a derived read model.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ResultServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResultServiceApplication.class, args);
    }
}
