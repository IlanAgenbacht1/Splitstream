package dev.splitstream.race;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The full topology, in-process. No broker, no Docker. */
class LeaderboardTopologyTest {

    private static final String RACE = "race-1";
    private static final String RUNNER = RACE + ":101";
    private static final Instant GUN = Instant.parse("2026-10-31T06:00:00Z");
    /** Degrees of latitude per metre, along a meridian, for the same Earth radius Geo uses. */
    private static final double DEG_PER_METER = 180 / (Math.PI * 6_371_000);

    private TopologyTestDriver driver;
    private TestInputTopic<String, RaceInfo> races;
    private TestInputTopic<String, LocationPing> pings;
    private KeyValueStore<String, RunnerProgress> progress;

    @BeforeEach
    void setUp() {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "race-service-test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        driver = new TopologyTestDriver(LeaderboardTopology.build("pings", "races"), config);
        races = driver.createInputTopic("races", new StringSerializer(), JsonSerdes.of(RaceInfo.class).serializer());
        pings = driver.createInputTopic("pings", new StringSerializer(), JsonSerdes.of(LocationPing.class).serializer());
        progress = driver.getKeyValueStore(LeaderboardTopology.PROGRESS_STORE);

        races.pipeInput(RACE, new RaceInfo(RACE, "Test 1K", 1000, GUN));
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void accumulatesDistanceFromConsecutivePings() {
        ping(0, 0);
        ping(60, 250);
        ping(120, 500);

        RunnerProgress runner = progress.get(RUNNER);
        assertThat(runner.distanceMeters()).isCloseTo(500, within(0.01));
        assertThat(runner.pings()).isEqualTo(3);
        assertThat(runner.hasFinished()).isFalse();
    }

    @Test
    void interpolatesTheFinishBetweenPings() {
        ping(0, 0);
        ping(180, 600);
        ping(360, 1200); // the 1000 m line is two-thirds of the way along this leg

        RunnerProgress runner = progress.get(RUNNER);
        assertThat(runner.distanceMeters()).isEqualTo(1000);
        assertThat(runner.finishedAt()).isCloseTo(GUN.plusSeconds(300), within(1, ChronoUnit.SECONDS));
        assertThat(runner.elapsed()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void recordsElapsedTimeAtEachKilometreForTheFinishPrediction() {
        races.pipeInput("race-3k", new RaceInfo("race-3k", "Test 3K", 3000, GUN));
        for (long[] fix : new long[][] {{0, 0}, {200, 800}, {400, 1600}, {700, 2600}}) {
            pings.pipeInput("race-3k:9", new LocationPing("race-3k", "9", fix[1] * DEG_PER_METER, 0, GUN.plusSeconds(fix[0])));
        }

        // 1000 m is a quarter of the way along the second leg, 2000 m two-fifths along the third
        assertThat(progress.get("race-3k:9").kmSplits())
                .containsExactly(Duration.ofSeconds(250), Duration.ofSeconds(520));
    }

    @Test
    void duplicatesAndLatePingsDoNotAddDistance() {
        ping(0, 0);
        ping(60, 250);
        ping(60, 250);  // a watch retry: same ping again
        ping(30, 400);  // a straggler from before the last ping

        RunnerProgress runner = progress.get(RUNNER);
        assertThat(runner.distanceMeters()).isCloseTo(250, within(0.01));
        assertThat(runner.pings()).isEqualTo(2);
    }

    @Test
    void pingsAfterTheFinishAreIgnored() {
        ping(0, 0);
        ping(300, 1000);
        ping(400, 1300); // warm-down jog

        assertThat(progress.get(RUNNER).distanceMeters()).isEqualTo(1000);
    }

    @Test
    void pingsForUnknownRacesAreDropped() {
        pings.pipeInput("ghost-race:7", new LocationPing("ghost-race", "7", 0, 0, GUN));

        assertThat(progress.get("ghost-race:7")).isNull();
    }

    private void ping(long secondsAfterGun, double metersNorth) {
        pings.pipeInput(RUNNER, new LocationPing(RACE, "101", metersNorth * DEG_PER_METER, 0, GUN.plusSeconds(secondsAfterGun)));
    }
}
