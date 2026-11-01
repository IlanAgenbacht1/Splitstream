# 3. Ordering, duplicates and where the leaderboard state lives

**Status:** accepted

## Context

Watches buffer pings while offline and retry on failure. The producer retries too. Delivery is therefore at-least-once, and a runner's pings can arrive twice or late.

## Decision

- **Key:** pings are keyed `raceId:bib`. All of one runner's pings land on one partition, in the order ingest-service sent them.
- **Idempotent aggregate:** `RunnerProgress.apply` only accepts a ping newer than the last one it applied. Duplicates and stragglers change nothing, so replays and retries are safe without exactly-once semantics.
- **Finish time** is interpolated along the leg where the runner crossed the line. It isn't rounded to the next ping.
- **State:** progress is a Kafka Streams state store (RocksDB, backed by a changelog topic). The leaderboard endpoint reads it with an interactive query.

## Consequences

- A straggler's distance is lost rather than merged in. At one ping a second that costs a few metres at worst. Reordering within a grace window is the upgrade if it ever matters.
- With more than one race-service instance, each one holds only its partitions' runners. The leaderboard endpoint would then fan out to peers using `KafkaStreams#streamsMetadataForStore`, or a small `leaderboard-updates` topic would feed a read model. This is deliberately not built yet.
- The bib can't contain `:`. Validation enforces that at the edge.
