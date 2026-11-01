package dev.splitstream.ingest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres, real Kafka. Asserts on the raw JSON on the wire, because that's the contract race-service reads.
 * Skipped, not failed, on machines without Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class IngestFlowIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaContainer kafka;

    @Test
    void raceAndPingsLandOnKafkaAsPlainJson() {
        MvcTestResult created = mvc.post().uri("/api/races")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Sea Point Promenade 5K", "distanceMeters": 5000, "startsAt": "2026-10-31T06:00:00Z"}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String location = created.getResponse().getHeader("Location");
        String raceId = location.substring(location.lastIndexOf('/') + 1);

        assertThat(mvc.get().uri("/api/races/{id}", raceId)).hasStatus(HttpStatus.OK);

        assertThat(mvc.post().uri("/api/races/{id}/pings", raceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bib": "101", "pings": [
                          {"lat": -33.8996, "lon": 18.4006, "recordedAt": "2026-10-31T06:00:00Z"},
                          {"lat": -33.8997, "lon": 18.4005, "recordedAt": "2026-10-31T06:00:01Z"}
                        ]}
                        """)).hasStatus(HttpStatus.ACCEPTED);

        List<ConsumerRecord<String, String>> races = consume("races", 1);
        assertThat(races).singleElement().satisfies(record -> {
            assertThat(record.key()).isEqualTo(raceId);
            assertThat(record.value()).contains("\"distanceMeters\":5000", "\"startsAt\":\"2026-10-31T06:00:00Z\"");
        });

        List<ConsumerRecord<String, String>> pings = consume("runner-pings", 2);
        assertThat(pings).hasSize(2).allSatisfy(record -> {
            assertThat(record.key()).isEqualTo(raceId + ":101");
            assertThat(record.headers().lastHeader("__TypeId__")).as("no Java type headers").isNull();
        });
        assertThat(pings.get(0).value()).contains("\"recordedAt\":\"2026-10-31T06:00:00Z\"");
    }

    @Test
    void pingsForUnknownRaceAreRejected() {
        assertThat(mvc.post().uri("/api/races/{id}/pings", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bib": "101", "pings": [{"lat": -33.9, "lon": 18.4, "recordedAt": "2026-10-31T06:00:00Z"}]}
                        """)).hasStatus(HttpStatus.NOT_FOUND);
    }

    private List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (received.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(250)).forEach(received::add);
            }
        }
        return received;
    }
}
