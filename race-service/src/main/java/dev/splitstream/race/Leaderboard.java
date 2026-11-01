package dev.splitstream.race;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import dev.splitstream.finish.FinishTimePredictor;

/** Ranking rules, kept apart from the state store so they're testable without Kafka. */
final class Leaderboard {

    /** Finishers first, in order of crossing the line. Then everyone else, furthest along first. */
    static final Comparator<RunnerProgress> RANKING = Comparator
            .comparing((RunnerProgress p) -> !p.hasFinished())
            .thenComparing(RunnerProgress::finishedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(RunnerProgress::distanceMeters, Comparator.reverseOrder())
            .thenComparing(RunnerProgress::lastSeen);

    private Leaderboard() {
    }

    static List<LeaderboardEntry> rank(Collection<RunnerProgress> runners, FinishTimePredictor predictor) {
        List<RunnerProgress> ordered = runners.stream().sorted(RANKING).toList();
        List<LeaderboardEntry> entries = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            RunnerProgress runner = ordered.get(i);
            Duration elapsed = runner.elapsed();
            Long predicted = runner.hasFinished() ? null : predictor
                    .predictFinish(runner.kmSplits(), runner.raceDistanceMeters())
                    .map(Duration::toSeconds)
                    .orElse(null);
            entries.add(new LeaderboardEntry(i + 1, runner.bib(), Math.round(runner.distanceMeters()),
                    elapsed.toSeconds(), pace(elapsed, runner.distanceMeters()), runner.hasFinished(),
                    runner.finishedAt(), predicted));
        }
        return entries;
    }

    /** Minutes:seconds per km, or null before the first 100 m, when it's mostly GPS noise. */
    static String pace(Duration elapsed, double meters) {
        if (meters < 100) {
            return null;
        }
        long secondsPerKm = Math.round(elapsed.toMillis() / meters);
        return "%d:%02d".formatted(secondsPerKm / 60, secondsPerKm % 60);
    }
}
