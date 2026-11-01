package dev.splitstream.finish;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Turns a runner's kilometre splits into the numbers the model sees. Shared by training and prediction, so the two
 * can't drift apart.
 * <p>
 * The model doesn't predict a finish time directly. It predicts a <em>fade factor</em>: how much slower than their
 * average pace so far the runner will cover the rest. 1.0 means even pace, 1.15 means 15% slower. That keeps one
 * model usable across a 5K and a marathon, and makes the naive "keep current pace" projection the baseline at 1.0.
 */
public final class FinishFeatures {

    /** Fewer than this and there's no trend to read, only noise. */
    public static final int MIN_KM = 3;

    static final String[] NAMES = {"raceKm", "fractionDone", "avgPaceSeconds", "recentPaceRatio", "projectedDrift"};

    final double raceKm;
    final double kmDone;
    final double elapsedSeconds;
    final double avgPace;
    final double trendRemainingSeconds;
    final double[] values;

    private FinishFeatures(double raceKm, double kmDone, double elapsedSeconds, double avgPace,
                           double trendRemainingSeconds, double[] values) {
        this.raceKm = raceKm;
        this.kmDone = kmDone;
        this.elapsedSeconds = elapsedSeconds;
        this.avgPace = avgPace;
        this.trendRemainingSeconds = trendRemainingSeconds;
        this.values = values;
    }

    /**
     * @param kmSplits elapsed time at each whole kilometre, in order
     * @return empty until there are {@link #MIN_KM} splits, and once the runner is inside the last kilometre
     */
    public static Optional<FinishFeatures> of(List<Duration> kmSplits, int raceDistanceMeters) {
        double raceKm = raceDistanceMeters / 1000.0;
        int n = kmSplits.size();
        if (n < MIN_KM || n >= raceKm - 1) {
            return Optional.empty();
        }

        double[] splitSeconds = new double[n];
        double previous = 0;
        for (int i = 0; i < n; i++) {
            double at = kmSplits.get(i).toMillis() / 1000.0;
            splitSeconds[i] = at - previous;
            previous = at;
        }
        double elapsed = previous;
        double avgPace = elapsed / n;
        double recent = (splitSeconds[n - 1] + splitSeconds[n - 2]) / 2;
        double slope = slope(splitSeconds);

        // The straight line through the splits so far, carried on to the finish
        double remainingKm = raceKm - n;
        double nextSplit = avgPace + slope * (n - 1) / 2.0 + slope;
        double trendRemaining = remainingKm * (nextSplit + slope * (remainingKm - 1) / 2.0);

        return Optional.of(new FinishFeatures(raceKm, n, elapsed, avgPace, trendRemaining, new double[] {
                raceKm,
                n / raceKm,
                avgPace,
                recent / avgPace,
                slope * raceKm / avgPace}));
    }

    /** What the naive projection says: the rest of the race at the average pace so far. */
    double evenPaceRemainingSeconds() {
        return (raceKm - kmDone) * avgPace;
    }

    Duration finishFor(double fade) {
        return Duration.ofMillis(Math.round((elapsedSeconds + evenPaceRemainingSeconds() * fade) * 1000));
    }

    /** Least-squares slope of split time against kilometre number: seconds per km gained (or lost) each km. */
    private static double slope(double[] y) {
        int n = y.length;
        double meanX = (n - 1) / 2.0;
        double meanY = 0;
        for (double v : y) {
            meanY += v / n;
        }
        double num = 0;
        double den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - meanX) * (y[i] - meanY);
            den += (i - meanX) * (i - meanX);
        }
        return num / den;
    }
}
