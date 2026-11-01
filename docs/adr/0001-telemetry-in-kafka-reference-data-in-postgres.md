# 1. Telemetry goes to Kafka, reference data goes to Postgres

**Status:** accepted

## Context

A race produces two very different kinds of data:

| | Races | GPS pings |
|---|---|---|
| Volume | A handful per day | One per runner per second |
| Shape | Edited, queried by id | Append-only, read in order |
| Lifetime | Forever | Useful for hours |

## Decision

- Races live in Postgres (ingest-service) and are also published to a compacted `races` topic.
- Pings are validated by ingest-service and go straight to the `runner-pings` topic. They are never written to Postgres.
- race-service builds its view (runner progress, leaderboards) from the two topics only. It has no database.

## Consequences

- A 1,000-runner race is about 1,000 messages a second, which is easy for Kafka. A row insert per ping would make Postgres the bottleneck for no benefit.
- race-service can be rebuilt from scratch by replaying the topics. This is the main operational advantage.
- Ping retention is a Kafka topic setting, not a cleanup job.
- Historic runs (if they're ever wanted) would come from a sink connector or a separate consumer. Ingest stays unchanged.
