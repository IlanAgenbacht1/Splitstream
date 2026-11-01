package dev.splitstream.race;

import java.time.Instant;

/** Read side of ingest-service's RaceCreatedEvent. */
public record RaceInfo(String raceId, String name, int distanceMeters, Instant startsAt) {
}
