package dev.splitstream.race;

import java.time.Duration;
import java.time.Instant;

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
        long pings) {

    public static RunnerProgress start() {
        return new RunnerProgress(null, null, 0, 0, null, null, null, 0, 0, 0);
    }

    public RunnerProgress apply(LocationPing ping, int raceDistance) {
        if (lastSeen == null) {
            return new RunnerProgress(ping.raceId(), ping.bib(), raceDistance, 0,
                    ping.recordedAt(), ping.recordedAt(), null, ping.lat(), ping.lon(), 1);
        }
        // Delivery is at-least-once and watches retry, so duplicates and stragglers happen.
        // Only pings newer than the last one count, which makes applying the same ping twice harmless.
        if (hasFinished() || !ping.recordedAt().isAfter(lastSeen)) {
            return this;
        }

        double leg = Geo.distanceMeters(lastLat, lastLon, ping.lat(), ping.lon());
        double covered = distanceMeters + leg;
        Instant finished = null;
        if (covered >= raceDistanceMeters) {
            // The line was crossed somewhere along this leg. Interpolate instead of rounding to the ping.
            double fraction = (raceDistanceMeters - distanceMeters) / leg;
            long legMillis = Duration.between(lastSeen, ping.recordedAt()).toMillis();
            finished = lastSeen.plusMillis(Math.round(legMillis * fraction));
            covered = raceDistanceMeters;
        }
        return new RunnerProgress(raceId, bib, raceDistanceMeters, covered,
                firstSeen, ping.recordedAt(), finished, ping.lat(), ping.lon(), pings + 1);
    }

    public boolean hasFinished() {
        return finishedAt != null;
    }

    /** Chip time: from the runner's first ping, not the gun. */
    public Duration elapsed() {
        return Duration.between(firstSeen, hasFinished() ? finishedAt : lastSeen);
    }
}
