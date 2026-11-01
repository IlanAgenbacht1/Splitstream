package dev.splitstream.ingest.events;

import java.time.Instant;
import java.util.UUID;

/** Wire contract for the races topic. Plain JSON, no Java type headers. */
public record RaceCreatedEvent(UUID raceId, String name, int distanceMeters, Instant startsAt) {
}
