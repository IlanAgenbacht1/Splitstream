package dev.splitstream.ingest.notes;

import com.anthropic.models.messages.MessageCreateParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the schema the model is held to. If an optional field loses its null type, structured outputs make the
 * model fill it in, and "Ran this morning" comes back with a made-up distance.
 */
class RunLogSchemaTest {

    @Test
    void numbersTheNoteMightNotMentionAreNullable() {
        String schema = MessageCreateParams.builder()
                .model("claude-opus-5-5")
                .maxTokens(1L)
                .outputConfig(RunLog.class)
                .addUserMessage("unused")
                .build()
                .rawParams().outputConfig().orElseThrow()
                .format().orElseThrow()
                .toString();

        assertThat(schema)
                .contains("distanceKm={type=[number, null]")
                .contains("durationMinutes={type=[integer, null]")
                .contains("perceivedEffort={type=[integer, null]")
                .contains("startedAtKm={type=[number, null]")
                .contains("additionalProperties=false");
    }
}
