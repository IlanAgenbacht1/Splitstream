package dev.splitstream.race;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Types Jackson builds by reflection inside the Kafka Streams serdes. Quarkus finds REST return types by itself, but it
 * can't see through {@link JsonSerdes}, and a native image drops whatever it can't see: the topology would start and
 * then fail on the first record.
 */
@RegisterForReflection(targets = {RaceInfo.class, LocationPing.class, RacePing.class, RunnerProgress.class,
        LeaderboardEntry.class})
final class NativeReflection {

    private NativeReflection() {
    }
}
