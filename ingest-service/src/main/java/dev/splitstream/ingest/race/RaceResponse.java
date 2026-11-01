package dev.splitstream.ingest.race;

import java.time.Instant;
import java.util.UUID;

public record RaceResponse(UUID id, String name, int distanceMeters, Instant startsAt) {

    static RaceResponse from(Race race) {
        return new RaceResponse(race.getId(), race.getName(), race.getDistanceMeters(), race.getStartsAt());
    }
}
