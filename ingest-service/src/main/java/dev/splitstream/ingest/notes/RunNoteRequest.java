package dev.splitstream.ingest.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunNoteRequest(@NotBlank @Size(max = 2000) String note) {
}
