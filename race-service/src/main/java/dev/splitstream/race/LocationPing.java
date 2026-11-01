package dev.splitstream.race;

import java.time.Instant;

/** Read side of ingest-service's LocationPingEvent. Unknown fields are ignored (tolerant reader). */
public record LocationPing(String raceId, String bib, double lat, double lon, Instant recordedAt) {
}
