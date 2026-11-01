package dev.splitstream.ingest.notes;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A run log extracted from a free-text note. The JSON schema sent to the model is generated from this record,
 * and the same record carries the Bean Validation rules its answer has to pass. See docs/adr/0005.
 *
 * <p>Fields the note might not mention are {@code Optional} on purpose: that's what makes the generated schema say
 * {@code "type": ["number", "null"]}. A plain {@code Double} becomes a required number, and the model is then forced
 * to invent one.
 */
public record RunLog(
        @JsonPropertyDescription("Distance run in kilometres, or null if the note doesn't say")
        Optional<@Positive @Max(250) Double> distanceKm,

        @JsonPropertyDescription("Total running time in whole minutes, or null if the note doesn't say")
        Optional<@Positive @Max(1440) Integer> durationMinutes,

        @NotNull RunType type,

        @JsonPropertyDescription("Perceived effort from 1 (very easy) to 10 (all out), or null if it can't be told from the note")
        Optional<@Min(1) @Max(10) Integer> perceivedEffort,

        @JsonPropertyDescription("Every pain, ache or tightness the runner mentions. Empty if none")
        @NotNull @Size(max = 10) List<@Valid Niggle> niggles,

        @JsonPropertyDescription("One neutral sentence summarising the run")
        @NotBlank @Size(max = 200) String summary) {

    public enum RunType { EASY, RECOVERY, LONG, TEMPO, INTERVALS, RACE, OTHER }

    public enum Side { LEFT, RIGHT, BOTH, UNSPECIFIED }

    public enum Severity { MILD, MODERATE, SEVERE }

    public record Niggle(
            @JsonPropertyDescription("Body part in plain English, e.g. knee, calf, lower back")
            @NotBlank @Size(max = 40) String bodyPart,

            @NotNull Side side,

            @JsonPropertyDescription("Kilometre mark where it started, or null if the note doesn't say")
            Optional<@PositiveOrZero @Max(250) Double> startedAtKm,

            @NotNull Severity severity) {
    }
}
