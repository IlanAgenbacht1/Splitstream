package dev.splitstream.race;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.ServiceUnavailableException;

import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

/**
 * Interactive query straight against the local state stores. With one instance that's the whole picture.
 * Scaling out needs a fan-out across instances. See docs/adr/0003.
 */
@Path("/api/races/{raceId}/leaderboard")
public class LeaderboardResource {

    @Inject
    KafkaStreams streams;

    @GET
    public List<LeaderboardEntry> leaderboard(@PathParam("raceId") String raceId) {
        try {
            if (store(LeaderboardTopology.RACES_STORE, RaceInfo.class).get(raceId) == null) {
                throw new NotFoundException("No race with id " + raceId);
            }
            List<RunnerProgress> runners = new ArrayList<>();
            try (KeyValueIterator<String, RunnerProgress> it =
                         store(LeaderboardTopology.PROGRESS_STORE, RunnerProgress.class)
                                 .prefixScan(raceId + ":", new StringSerializer())) {
                it.forEachRemaining((KeyValue<String, RunnerProgress> kv) -> runners.add(kv.value));
            }
            return Leaderboard.rank(runners);
        }
        catch (InvalidStateStoreException ex) {
            // Startup or rebalance. The data is fine, it just isn't queryable this second.
            throw new ServiceUnavailableException(1L, ex);
        }
    }

    private <V> ReadOnlyKeyValueStore<String, V> store(String name, Class<V> valueType) {
        return streams.store(StoreQueryParameters.fromNameAndType(name, QueryableStoreTypes.<String, V>keyValueStore()));
    }
}
