# 4. Dual write on race creation (known gap)

**Status:** accepted for now, superseded when the outbox lands

## Context

Creating a race writes a row to Postgres **and** publishes to the `races` topic. The two can't be made atomic directly.

## Decision (for now)

The publish happens inside the database transaction and blocks on the broker's ack:

- Publish fails → exception → the insert rolls back, and the client gets a 503. **Consistent.**
- Publish succeeds, then the commit fails → Kafka knows about a race Postgres doesn't. **Inconsistent, but rare.**

## Why not fix it now

Races are created a few times a day, by an organiser who can retry. The failure window is a commit failing after a successful ack, which is small and recoverable by hand. Pings aren't affected because they don't touch the database.

## The fix

A transactional outbox. Insert the event into an `outbox` table in the same transaction as the race, then relay it to Kafka with a poller or Debezium CDC. This is on the roadmap. It's the same pattern used to bolt event streams onto legacy systems that can only write to their own database.
