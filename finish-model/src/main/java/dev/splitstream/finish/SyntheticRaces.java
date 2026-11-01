package dev.splitstream.finish;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Training data. There's no public split dataset in the repo, so races are generated from a pacing model with the
 * shape real fields have: some runners fade steadily, a few negative-split, and the ones already fading early are the
 * likeliest to hit the wall late on, harder over longer distances. Each km gets a little noise on top.
 * <p>
 * The device simulator uses the same model, so live predictions are judged against the kind of race they were
 * trained on. Real results would replace this class and nothing else. See docs/adr/0006.
 */
final class SyntheticRaces {

    static final int[] DISTANCES = {5_000, 10_000, 21_098, 42_195};

    record Runner(int raceDistanceMeters, List<Duration> kmSplits, Duration finish) {
    }

    private SyntheticRaces() {
    }

    static List<Runner> generate(int count, long seed) {
        Random random = new Random(seed);
        List<Runner> runners = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            runners.add(runner(DISTANCES[random.nextInt(DISTANCES.length)], random));
        }
        return runners;
    }

    static Runner runner(int raceDistanceMeters, Random random) {
        PacingProfile pacing = PacingProfile.random(raceDistanceMeters, random);
        double raceKm = raceDistanceMeters / 1000.0;

        List<Duration> splits = new ArrayList<>();
        double elapsed = 0;
        for (int km = 1; km <= Math.floor(raceKm); km++) {
            elapsed += pacing.paceAt((km - 0.5) / raceKm) * (1 + random.nextGaussian() * 0.02);
            splits.add(Duration.ofMillis(Math.round(elapsed * 1000)));
        }
        double lastBit = raceKm - Math.floor(raceKm);
        elapsed += pacing.paceAt(1.0) * lastBit;
        return new Runner(raceDistanceMeters, splits, Duration.ofMillis(Math.round(elapsed * 1000)));
    }
}
