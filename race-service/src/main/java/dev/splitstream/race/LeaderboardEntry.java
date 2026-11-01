package dev.splitstream.race;

import java.time.Instant;

public record LeaderboardEntry(
        int position,
        String bib,
        long distanceMeters,
        long elapsedSeconds,
        String pacePerKm,
        boolean finished,
        Instant finishedAt) {
}
