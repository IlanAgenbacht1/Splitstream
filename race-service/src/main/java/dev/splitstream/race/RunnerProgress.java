package dev.splitstream.race;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Running state for one runner in one race: the value in the Kafka Streams aggregate.
 * Immutable. Every ping produces a new value, which keeps the aggregator a pure function and easy to test.
 */
public record RunnerProgress(
        String raceId,
        String bib,
        int raceDistanceMeters,
        double distanceMeters,
        Instant firstSeen,
        Instant lastSeen,
        Instant finishedAt,
        double lastLat,
        double lastLon,
        long pings,
        List<Long> kmSplitMillis) {

    /** Elapsed millis at each whole km. Feeds the finish-time prediction. */
    public RunnerProgress {
        kmSplitMillis = kmSplitMillis == null ? List.of() : List.copyOf(kmSplitMillis);
    }

    public static RunnerProgress start() {
        return new RunnerProgress(null, null, 0, 0, null, null, null, 0, 0, 0, List.of());
    }

    public RunnerProgress apply(LocationPing ping, int raceDistance) {
        if (lastSeen == null) {
            return new RunnerProgress(ping.raceId(), ping.bib(), raceDistance, 0,
                    ping.recordedAt(), ping.recordedAt(), null, ping.lat(), ping.lon(), 1, List.of());
        }
        // Delivery is at-least-once and watches retry, so duplicates and stragglers happen.
        // Only pings newer than the last one count, which makes applying the same ping twice harmless.
        if (hasFinished() || !ping.recordedAt().isAfter(lastSeen)) {
            return this;
        }

        double leg = Geo.distanceMeters(lastLat, lastLon, ping.lat(), ping.lon());
        double covered = Math.min(distanceMeters + leg, raceDistanceMeters);
        long legMillis = Duration.between(lastSeen, ping.recordedAt()).toMillis();

        // Lines crossed somewhere along this leg (km marks, the finish) are interpolated, not rounded to the ping
        List<Long> splits = kmSplitMillis;
        int nextKm = splits.size() + 1;
        if (nextKm * 1000.0 <= covered) {
            splits = new ArrayList<>(kmSplitMillis);
            for (; nextKm * 1000.0 <= covered; nextKm++) {
                splits.add(Duration.between(firstSeen, crossedAt(nextKm * 1000.0, leg, legMillis)).toMillis());
            }
        }
        Instant finished = covered >= raceDistanceMeters ? crossedAt(raceDistanceMeters, leg, legMillis) : null;
        return new RunnerProgress(raceId, bib, raceDistanceMeters, covered,
                firstSeen, ping.recordedAt(), finished, ping.lat(), ping.lon(), pings + 1, splits);
    }

    private Instant crossedAt(double mark, double leg, long legMillis) {
        double fraction = (mark - distanceMeters) / leg;
        return lastSeen.plusMillis(Math.round(legMillis * fraction));
    }

    public List<Duration> kmSplits() {
        return kmSplitMillis.stream().map(Duration::ofMillis).toList();
    }

    public boolean hasFinished() {
        return finishedAt != null;
    }

    /** Chip time: from the runner's first ping, not the gun. */
    public Duration elapsed() {
        return Duration.between(firstSeen, hasFinished() ? finishedAt : lastSeen);
    }
}
