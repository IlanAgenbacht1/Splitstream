package dev.splitstream.race;

/** A ping joined with the one fact about its race the aggregate needs. */
public record RacePing(LocationPing ping, int raceDistanceMeters) {
}
