package dev.splitstream.ingest.events;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * The one place that knows topic names and message keys.
 * <p>
 * Sends block until the broker acknowledges (acks=all). A device only gets its 202 once its pings are durable, and a
 * failed send becomes a 503 the device can retry. Duplicates from those retries are handled downstream.
 */
@Component
public class RaceEventPublisher {

    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(5);

    private final KafkaTemplate<String, Object> kafka;
    private final Topics topics;

    RaceEventPublisher(KafkaTemplate<String, Object> kafka, Topics topics) {
        this.kafka = kafka;
        this.topics = topics;
    }

    public void raceCreated(RaceCreatedEvent event) {
        sendAndAwait(topics.races(), event.raceId().toString(), List.of(event));
    }

    /** One key per runner keeps each runner's pings on one partition, in order. */
    public void locationPings(UUID raceId, String bib, List<LocationPingEvent> pings) {
        sendAndAwait(topics.pings(), runnerKey(raceId, bib), pings);
    }

    public static String runnerKey(UUID raceId, String bib) {
        return raceId + ":" + bib;
    }

    private void sendAndAwait(String topic, String key, List<?> events) {
        CompletableFuture<?>[] sends = events.stream()
                .map(event -> kafka.send(topic, key, event))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(sends).get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException(topic, ex);
        }
        catch (ExecutionException | TimeoutException ex) {
            throw new PublishFailedException(topic, ex);
        }
    }
}
