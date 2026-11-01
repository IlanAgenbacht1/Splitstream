package dev.splitstream.race;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;

/**
 * One explicit mapper for every topic and state store, so the topology behaves the same in production and under
 * TopologyTestDriver, where there's no CDI container to hand one over.
 */
final class JsonSerdes {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonSerdes() {
    }

    static <T> ObjectMapperSerde<T> of(Class<T> type) {
        return new ObjectMapperSerde<>(type, MAPPER);
    }
}
