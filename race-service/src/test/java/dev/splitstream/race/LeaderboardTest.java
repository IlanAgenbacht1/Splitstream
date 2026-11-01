package dev.splitstream.race;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.splitstream.finish.FinishTimePredictor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardTest {

    private static final Instant GUN = Instant.parse("2026-10-31T06:00:00Z");
    private static final FinishTimePredictor NO_PREDICTION = (splits, distance) -> Optional.empty();

    @Test
    void finishersRankByCrossingTimeAheadOfEveryoneStillRunning() {
        RunnerProgress slowFinisher = runner("1", 5000, GUN.plusSeconds(1700));
        RunnerProgress fastFinisher = runner("2", 5000, GUN.plusSeconds(1300));
        RunnerProgress nearlyThere = runner("3", 4990, null);
        RunnerProgress backOfPack = runner("4", 2100, null);

        List<LeaderboardEntry> board = Leaderboard.rank(List.of(backOfPack, slowFinisher, nearlyThere, fastFinisher), NO_PREDICTION);

        assertThat(board).extracting(LeaderboardEntry::bib).containsExactly("2", "1", "3", "4");
        assertThat(board).extracting(LeaderboardEntry::position).containsExactly(1, 2, 3, 4);
    }

    @Test
    void onlyRunnersStillOnTheCourseGetAPredictedFinish() {
        FinishTimePredictor in25Minutes = (splits, distance) -> Optional.of(Duration.ofMinutes(25));

        List<LeaderboardEntry> board = Leaderboard.rank(
                List.of(runner("1", 5000, GUN.plusSeconds(1300)), runner("2", 3000, null)), in25Minutes);

        assertThat(board).extracting(LeaderboardEntry::predictedFinishSeconds).containsExactly(null, 1500L);
    }

    @Test
    void paceIsMinutesAndSecondsPerKilometre() {
        assertThat(Leaderboard.pace(Duration.ofSeconds(1500), 5000)).isEqualTo("5:00");
        assertThat(Leaderboard.pace(Duration.ofSeconds(1234), 5000)).isEqualTo("4:07");
    }

    @Test
    void noPaceUntilTheRunnerHasMovedMeaningfully() {
        assertThat(Leaderboard.pace(Duration.ofSeconds(20), 40)).isNull();
    }

    private static RunnerProgress runner(String bib, double meters, Instant finishedAt) {
        Instant lastSeen = finishedAt != null ? finishedAt : GUN.plusSeconds(1600);
        return new RunnerProgress("race-1", bib, 5000, meters, GUN, lastSeen, finishedAt, 0, 0, 10, List.of());
    }
}
