package dev.splitstream.race;

import java.time.Instant;

public record LeaderboardEntry(
        int position,
        String bib,
        long distanceMeters,
        long elapsedSeconds,
        String pacePerKm,
        boolean finished,
        Instant finishedAt,
        /** Predicted chip time at the finish. Null once finished, and until there are 3 km of splits to go on. */
        Long predictedFinishSeconds) {
}
