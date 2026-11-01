package dev.splitstream.simulator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * A field of fake running watches on the Sea Point promenade, Cape Town.
 * <p>
 * Each watch records one GPS fix per simulated second and uploads its buffer once per real second, so
 * {@code --speedup=20} means 20-ping batches and a 25-minute 5K in about 75 seconds. A failed upload stays buffered
 * and goes again next tick, the way a real watch behaves on bad signal.
 * <p>
 * JDK only. Run with {@code java -jar device-simulator/target/device-simulator.jar [--runners=12] [--speedup=20]}.
 */
public final class DeviceSimulator {

    /** Mouille Point lighthouse to Sea Point pavilion. Runners go out and back, and loop if the race is longer. */
    private static final double[][] PROMENADE = {
            {-33.8996, 18.4006}, {-33.9030, 18.3990}, {-33.9068, 18.3953},
            {-33.9110, 18.3900}, {-33.9150, 18.3850}, {-33.9185, 18.3815},
    };

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String baseUrl = options.getOrDefault("url", "http://localhost:8080");
        int runnerCount = Integer.parseInt(options.getOrDefault("runners", "12"));
        int speedup = Integer.parseInt(options.getOrDefault("speedup", "20"));
        int distance = Integer.parseInt(options.getOrDefault("distance", "5000"));
        long seed = Long.parseLong(options.getOrDefault("seed", String.valueOf(System.nanoTime())));

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        Route route = Route.outAndBack(PROMENADE);
        Instant gun = Instant.now();
        String raceId = createRace(http, baseUrl, "Sea Point Promenade " + distance / 1000 + "K", distance, gun);

        Random random = new Random(seed);
        List<Runner> field = new ArrayList<>();
        for (int i = 0; i < runnerCount; i++) {
            field.add(Runner.random(String.valueOf(101 + i), distance, random));
        }

        System.out.printf("Race %s: %d runners, %d m, %dx speed%n", raceId, runnerCount, distance, speedup);
        System.out.printf("Leaderboard: http://localhost:8081/api/races/%s/leaderboard%n", raceId);

        long simSecond = 0;
        while (field.stream().anyMatch(runner -> !runner.isDone())) {
            for (int s = 0; s < speedup; s++) {
                simSecond++;
                Instant now = gun.plusSeconds(simSecond);
                for (Runner runner : field) {
                    runner.tick(now, distance, route);
                }
            }
            for (Runner runner : field) {
                runner.upload(http, baseUrl, raceId);
            }
            printStandings(field, distance);
            Thread.sleep(1000);
        }
        System.out.println("All runners home.");
    }

    private static String createRace(HttpClient http, String baseUrl, String name, int distance, Instant startsAt)
            throws IOException, InterruptedException {
        String body = """
                {"name": "%s", "distanceMeters": %d, "startsAt": "%s"}""".formatted(name, distance, startsAt);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/races"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IllegalStateException("Could not create race: " + response.statusCode() + " " + response.body());
        }
        String location = response.headers().firstValue("Location").orElseThrow();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static void printStandings(List<Runner> field, int distance) {
        String standings = field.stream()
                .sorted((a, b) -> Double.compare(b.meters, a.meters))
                .limit(5)
                .map(runner -> "%s@%.0fm".formatted(runner.bib, Math.min(runner.meters, distance)))
                .collect(Collectors.joining("  "));
        System.out.println("watches say: " + standings);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                options.put(arg.substring(2, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
            }
        }
        return options;
    }

    /** A polyline with cumulative distances, so a position can be looked up by metres run. */
    record Route(double[][] points, double[] cumulative) {

        static Route outAndBack(double[][] out) {
            List<double[]> points = new ArrayList<>(List.of(out));
            for (int i = out.length - 2; i >= 0; i--) {
                points.add(out[i]);
            }
            double[][] path = points.toArray(double[][]::new);
            double[] cumulative = new double[path.length];
            for (int i = 1; i < path.length; i++) {
                cumulative[i] = cumulative[i - 1] + haversine(path[i - 1], path[i]);
            }
            return new Route(path, cumulative);
        }

        double length() {
            return cumulative[cumulative.length - 1];
        }

        double[] positionAt(double meters) {
            double along = meters % length();
            int i = 1;
            while (cumulative[i] < along) {
                i++;
            }
            double fraction = (along - cumulative[i - 1]) / (cumulative[i] - cumulative[i - 1]);
            double[] a = points[i - 1];
            double[] b = points[i];
            return new double[] {a[0] + (b[0] - a[0]) * fraction, a[1] + (b[1] - a[1]) * fraction};
        }

        private static double haversine(double[] a, double[] b) {
            double dLat = Math.toRadians(b[0] - a[0]);
            double dLon = Math.toRadians(b[1] - a[1]);
            double h = Math.pow(Math.sin(dLat / 2), 2)
                    + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.pow(Math.sin(dLon / 2), 2);
            return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
        }
    }

    static final class Runner {

        final String bib;
        /** Starting pace in seconds per km. */
        final double basePace;
        /** How much pace drifts by the finish: positive fades, negative is a negative split. Makes overtakes happen. */
        final double drift;
        /** Fraction of the race where this runner hits the wall, or 2.0 if they never do. */
        final double wallAt;
        final double wallSlowdown;
        final List<String> buffer = new ArrayList<>();
        double meters;
        boolean crossedLine;
        boolean uploadedFinal;

        Runner(String bib, double basePace, double drift, double wallAt, double wallSlowdown) {
            this.bib = bib;
            this.basePace = basePace;
            this.drift = drift;
            this.wallAt = wallAt;
            this.wallSlowdown = wallSlowdown;
        }

        /** Same pacing model the finish-time predictor is trained on (finish-model's PacingProfile). */
        static Runner random(String bib, int raceDistance, Random random) {
            double drift = -0.08 + random.nextDouble() * 0.23;
            double wallChance = Math.min(0.9, (0.05 + 3 * Math.max(drift, 0)) * raceDistance / 42_195.0);
            boolean hitsWall = random.nextDouble() < wallChance;
            return new Runner(bib, 200 + random.nextDouble() * 220, drift,
                    hitsWall ? 0.6 + random.nextDouble() * 0.25 : 2.0,
                    hitsWall ? 0.10 + random.nextDouble() * 0.20 : 0.0);
        }

        void tick(Instant now, int raceDistance, Route route) {
            if (crossedLine) {
                return;
            }
            double progress = Math.min(meters / raceDistance, 1);
            double wall = progress >= wallAt ? wallSlowdown : 0;
            meters += 1000 / (basePace * (1 + drift * progress + wall));
            double[] fix = route.positionAt(meters);
            buffer.add(String.format(Locale.ROOT, "{\"lat\":%.6f,\"lon\":%.6f,\"recordedAt\":\"%s\"}", fix[0], fix[1], now));
            crossedLine = meters >= raceDistance;
        }

        void upload(HttpClient http, String baseUrl, String raceId) {
            if (buffer.isEmpty()) {
                return;
            }
            String body = "{\"bib\":\"" + bib + "\",\"pings\":[" + String.join(",", buffer) + "]}";
            try {
                HttpResponse<Void> response = http.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + "/api/races/" + raceId + "/pings"))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofSeconds(5))
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 202) {
                    buffer.clear();
                    uploadedFinal = crossedLine;
                }
                else {
                    System.out.printf("bib %s: upload got %d, keeping %d pings for next time%n", bib, response.statusCode(), buffer.size());
                }
            }
            catch (IOException ex) {
                System.out.printf("bib %s: no signal (%s), keeping %d pings%n", bib, ex.getClass().getSimpleName(), buffer.size());
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        boolean isDone() {
            return crossedLine && uploadedFinal;
        }
    }
}
