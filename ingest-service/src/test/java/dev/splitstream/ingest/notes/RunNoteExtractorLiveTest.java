package dev.splitstream.ingest.notes;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import dev.splitstream.ingest.notes.RunLog.RunType;
import dev.splitstream.ingest.notes.RunLog.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A small eval against the real model: notes with a known right answer. Costs real API calls, so it only runs
 * when ANTHROPIC_API_KEY is set. CI doesn't set it.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class RunNoteExtractorLiveTest {

    static AnthropicClient client;
    static RunNoteExtractor extractor;

    @BeforeAll
    static void connect() {
        client = AnthropicOkHttpClient.fromEnv();
        extractor = new ClaudeRunNoteExtractor(client, "claude-opus-5-5");
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @Test
    void readsDistanceTimeAndWhereThePainStarted() {
        RunLog log = extractor.extract("Easy 10k this morning, 58 min. Left knee got sore after about 7k.");

        assertThat(log.type()).isEqualTo(RunType.EASY);
        assertThat(log.distanceKm()).hasValueSatisfying(km -> assertThat(km).isCloseTo(10.0, within(0.01)));
        assertThat(log.durationMinutes()).contains(58);
        assertThat(log.niggles()).singleElement().satisfies(n -> {
            assertThat(n.bodyPart()).containsIgnoringCase("knee");
            assertThat(n.side()).isEqualTo(Side.LEFT);
            assertThat(n.startedAtKm()).hasValueSatisfying(km -> assertThat(km).isCloseTo(7.0, within(0.5)));
        });
    }

    @Test
    void convertsMilesAndHourMinuteTimes() {
        RunLog log = extractor.extract("Long run, 13.1 miles in 1h52. Legs heavy at the end but no pain.");

        assertThat(log.type()).isEqualTo(RunType.LONG);
        assertThat(log.distanceKm()).hasValueSatisfying(km -> assertThat(km).isCloseTo(21.1, within(0.2)));
        assertThat(log.durationMinutes()).contains(112);
        assertThat(log.niggles()).isEmpty();
    }

    @Test
    void leavesOutNumbersTheNoteDoesNotGive() {
        RunLog log = extractor.extract("Quick shakeout before work, felt great.");

        assertThat(log.distanceKm()).isEmpty();
        assertThat(log.durationMinutes()).isEmpty();
    }

    @Test
    void recognisesIntervals() {
        RunLog log = extractor.extract("6 x 800m at 5k pace, 90s jog recoveries. Right calf tight on the last two.");

        assertThat(log.type()).isEqualTo(RunType.INTERVALS);
        assertThat(log.niggles()).singleElement().satisfies(n -> assertThat(n.side()).isEqualTo(Side.RIGHT));
    }
}
