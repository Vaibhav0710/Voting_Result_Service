# 📊 Result Service

> Real-time vote aggregation and live election results — a stateless microservice powered by Kafka + Redis.

## Overview

| Attribute | Detail |
|-----------|--------|
| **Responsibility** | Real-time vote tallying, live results, audit reporting |
| **Database** | **None** — stateless aggregation service |
| **Port** | `8084` |
| **Data Source** | Kafka `vote.cast` events + Redis counters |
| **Dependencies** | Candidate Service (enrichment), Voting Service (audit) |

## Architecture

```
                    ┌─────────────────────────────────────┐
                    │          RESULT SERVICE             │
                    │                                     │
  vote.cast ──────► │  Kafka Consumer                     │
  (Kafka topic)     │       │                             │
                    │       ▼                             │
                    │  ResultTallyService ──► Redis       │
                    │       │              (HINCRBY)      │
                    │       ▼                             │
                    │  LiveResultService ──► SSE Emitters │
                    │                                     │
                    │  ResultController ◄─────────────────│
                    │   /results    (public)              │
                    │   /summary   (public)               │
                    │   /live      (SSE, public)          │
                    │   /audit     (ADMIN only)           │
                    └─────────────────────────────────────┘
                            │              │
                    Feign ──┘              └── Feign
                            ▼                      ▼
                    Candidate Service      Voting Service
                    (names, parties)       (audit data)
```

## API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `GET` | `/api/v1/results/election/{electionId}` | Raw vote counts per candidate | Public |
| `GET` | `/api/v1/results/election/{electionId}/summary` | Results with percentages + winner | Public |
| `GET` | `/api/v1/results/election/{electionId}/live` | SSE stream of live updates | Public |
| `GET` | `/api/v1/results/election/{electionId}/audit` | Full audit trail with hash data | ADMIN |

## Redis Data Model

| Key Pattern | Type | Description |
|------------|------|-------------|
| `res:votes:{electionId}` | Hash | `candidateId → voteCount` |
| `res:total:{electionId}` | String | Total vote count |
| `res:seen:{electionId}` | Set | Vote ids already counted (redelivery de-duplication) |

Counting runs as one Lua script (`SADD` seen → `HINCRBY` + `INCR` only if the vote id is new), so it is atomic and
a redelivered `vote.cast` event is never counted twice.

## Kafka Topics

| Topic | Role | Group ID |
|-------|------|----------|
| `vote.cast` | Consumer | `result-service-group` |

## Inter-Service Communication

| Target | Method | Purpose |
|--------|--------|---------|
| Candidate Service | OpenFeign (`/candidates/election/{id}`, retry ×3 + circuit breaker) | Enrich results with names/parties, including disqualified or withdrawn candidates |
| Voting Service | OpenFeign (`/votes/election/{id}`, 500 per page, caller JWT forwarded) | Fetch vote data for the audit trail; failure → 503, never an empty audit |

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| No database | ✅ | Pure derived read model; Voting DB is source of truth |
| Redis counters | ✅ | Atomic increments, sub-millisecond reads |
| SSE over WebSocket | ✅ | Simpler, HTTP-native, unidirectional push |
| Graceful degradation | ✅ | Returns "Unknown Candidate" if Candidate Service is down |
| Idempotent consumer | ✅ | Kafka is at-least-once; vote ids make counting exactly-once in effect |

## Running Locally

### Prerequisites
- Redis running on `localhost:6379`
- Kafka running on `localhost:9092`
- Eureka Server running on `localhost:8761`
- Candidate Service running on `localhost:8082`
- Voting Service running on `localhost:8083`

### Environment
`JWT_SECRET` (required, same Base64 secret as user-service). Optional: `REDIS_HOST`, `REDIS_PORT`,
`KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`.

### Start
```bash
mvn spring-boot:run
```

### Verify
```bash
# Health check
curl http://localhost:8084/actuator/health

# Get election results
curl http://localhost:8084/api/v1/results/election/{electionId}

# SSE live stream
curl -N http://localhost:8084/api/v1/results/election/{electionId}/live
```

## Project Structure

```
src/main/java/com/voting/resultservice/
├── ResultServiceApplication.java
├── client/         → Feign clients (Candidate + Voting)
├── config/         → Kafka consumer config, Feign config (JWT forwarding)
├── controller/     → REST endpoints
├── dto/            → Response DTOs
├── event/          → Kafka event DTOs
├── exception/      → Error handling
├── security/       → JWT authentication
└── service/        → Business logic (tally, SSE, consumer)
```
