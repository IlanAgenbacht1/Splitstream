package dev.splitstream.finish;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FinishFeaturesTest {

    @Test
    void nothingToSayBeforeThreeKilometres() {
        assertThat(FinishFeatures.of(evenSplits(2, 300), 10_000)).isEmpty();
    }

    @Test
    void nothingToSayInsideTheLastKilometre() {
        assertThat(FinishFeatures.of(evenSplits(9, 300), 10_000)).isEmpty();
    }

    @Test
    void evenPacingHasNoTrend() {
        FinishFeatures features = FinishFeatures.of(evenSplits(4, 300), 10_000).orElseThrow();

        assertThat(features.values[FinishFeaturesIndex.RECENT_PACE_RATIO]).isCloseTo(1.0, within(1e-9));
        assertThat(features.values[FinishFeaturesIndex.PROJECTED_DRIFT]).isCloseTo(0.0, within(1e-9));
        assertThat(features.evenPaceRemainingSeconds()).isCloseTo(6 * 300, within(1e-9));
        assertThat(features.trendRemainingSeconds).isCloseTo(6 * 300, within(1e-9));
    }

    @Test
    void slowingDownShowsUpAsPositiveDrift() {
        // 300, 310, 320, 330 s per km: 10 s slower every km
        List<Duration> splits = List.of(seconds(300), seconds(610), seconds(930), seconds(1260));
        FinishFeatures features = FinishFeatures.of(splits, 10_000).orElseThrow();

        assertThat(features.values[FinishFeaturesIndex.PROJECTED_DRIFT]).isCloseTo(10 * 10 / 315.0, within(1e-9));
        // The trend carries on: 340 + 350 + ... + 390
        assertThat(features.trendRemainingSeconds).isCloseTo(2190, within(1e-6));
    }

    private static List<Duration> evenSplits(int km, int secondsPerKm) {
        return IntStream.rangeClosed(1, km).mapToObj(i -> seconds(i * secondsPerKm)).toList();
    }

    private static Duration seconds(long s) {
        return Duration.ofSeconds(s);
    }

    /** Positions in {@link FinishFeatures#NAMES}. */
    private static final class FinishFeaturesIndex {
        static final int RECENT_PACE_RATIO = List.of(FinishFeatures.NAMES).indexOf("recentPaceRatio");
        static final int PROJECTED_DRIFT = List.of(FinishFeatures.NAMES).indexOf("projectedDrift");
    }
}
