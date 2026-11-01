package dev.splitstream.ingest.ping;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What a watch uploads. Watches buffer pings while offline, so a batch can cover minutes of running.
 * The bib pattern rules out ':' because it is the separator in the runner's message key.
 */
public record PingBatch(
        @NotBlank @Size(max = 10) @Pattern(regexp = "[A-Za-z0-9-]+") String bib,
        @NotEmpty @Size(max = 600) List<@Valid @NotNull Ping> pings) {

    public record Ping(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
            @NotNull Instant recordedAt) {
    }
}
