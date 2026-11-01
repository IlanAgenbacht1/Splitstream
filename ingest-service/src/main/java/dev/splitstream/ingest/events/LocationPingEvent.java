package dev.splitstream.ingest.events;

import java.time.Instant;
import java.util.UUID;

/** Wire contract for the pings topic. Plain JSON, no Java type headers. */
public record LocationPingEvent(UUID raceId, String bib, double lat, double lon, Instant recordedAt) {
}
