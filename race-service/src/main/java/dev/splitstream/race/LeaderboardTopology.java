package dev.splitstream.race;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * pings (keyed raceId:bib) ⋈ races (GlobalKTable) → per-runner aggregate → queryable "runner-progress" store.
 * <p>
 * The join with a GlobalKTable needs no co-partitioning or repartition: every instance holds all races, and the
 * ping key never changes, so each runner's pings are aggregated on the partition they arrived on.
 */
@ApplicationScoped
public class LeaderboardTopology {

    public static final String PROGRESS_STORE = "runner-progress";
    public static final String RACES_STORE = "races";

    @Produces
    Topology topology(@ConfigProperty(name = "splitstream.topics.pings") String pingsTopic,
                      @ConfigProperty(name = "splitstream.topics.races") String racesTopic) {
        return build(pingsTopic, racesTopic);
    }

    public static Topology build(String pingsTopic, String racesTopic) {
        StreamsBuilder builder = new StreamsBuilder();

        GlobalKTable<String, RaceInfo> races = builder.globalTable(racesTopic,
                Consumed.with(Serdes.String(), JsonSerdes.of(RaceInfo.class)),
                Materialized.<String, RaceInfo, KeyValueStore<Bytes, byte[]>>as(RACES_STORE)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(JsonSerdes.of(RaceInfo.class)));

        builder.stream(pingsTopic, Consumed.with(Serdes.String(), JsonSerdes.of(LocationPing.class)))
                // Inner join: pings for a race we've never heard of are dropped, not guessed at.
                .join(races,
                        (runnerKey, ping) -> ping.raceId(),
                        (ping, race) -> new RacePing(ping, race.distanceMeters()))
                .groupByKey(Grouped.with(Serdes.String(), JsonSerdes.of(RacePing.class)))
                .aggregate(RunnerProgress::start,
                        (runnerKey, racePing, progress) -> progress.apply(racePing.ping(), racePing.raceDistanceMeters()),
                        Materialized.<String, RunnerProgress, KeyValueStore<Bytes, byte[]>>as(PROGRESS_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(JsonSerdes.of(RunnerProgress.class)));

        return builder.build();
    }
}
