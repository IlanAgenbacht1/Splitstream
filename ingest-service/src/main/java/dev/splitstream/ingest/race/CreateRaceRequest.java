package dev.splitstream.ingest.race;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRaceRequest(
        @NotBlank @Size(max = 120) String name,
        @Positive @Max(250_000) int distanceMeters,
        @NotNull Instant startsAt) {
}
