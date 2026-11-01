package dev.splitstream.finish;

import java.util.Random;

/**
 * How one runner's pace changes over a race. Pace is seconds per km at a given fraction of the distance.
 *
 * @param basePace     seconds per km at the start
 * @param drift        steady change by the finish: 0.10 is 10% slower, negative is a negative split
 * @param wallAt       fraction of the race where the wall hits, or 2.0 if it never does
 * @param wallSlowdown extra slowdown once it hits
 */
record PacingProfile(double basePace, double drift, double wallAt, double wallSlowdown) {

    static PacingProfile random(int raceDistanceMeters, Random random) {
        double basePace = 200 + random.nextDouble() * 220;
        double drift = -0.08 + random.nextDouble() * 0.23;
        // Runners fading early are the ones who went out too fast, and the wall is mostly a long-race thing
        double wallChance = Math.min(0.9, (0.05 + 3 * Math.max(drift, 0)) * raceDistanceMeters / 42_195.0);
        boolean hitsWall = random.nextDouble() < wallChance;
        return new PacingProfile(basePace, drift,
                hitsWall ? 0.6 + random.nextDouble() * 0.25 : 2.0,
                hitsWall ? 0.10 + random.nextDouble() * 0.20 : 0.0);
    }

    double paceAt(double fraction) {
        double wall = fraction >= wallAt ? wallSlowdown : 0;
        return basePace * (1 + drift * fraction + wall);
    }
}
