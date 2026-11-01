package dev.splitstream.ingest.ping;

import java.util.List;
import java.util.UUID;

import dev.splitstream.ingest.events.LocationPingEvent;
import dev.splitstream.ingest.events.RaceEventPublisher;
import dev.splitstream.ingest.race.RaceService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pings are telemetry. They go straight to Kafka and never touch Postgres. See docs/adr/0001. */
@RestController
@RequestMapping("/api/races/{raceId}/pings")
class PingController {

    private final RaceService races;
    private final RaceEventPublisher events;

    PingController(RaceService races, RaceEventPublisher events) {
        this.races = races;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    void ingest(@PathVariable UUID raceId, @Valid @RequestBody PingBatch batch) {
        races.requireExists(raceId);
        List<LocationPingEvent> pings = batch.pings().stream()
                .map(ping -> new LocationPingEvent(raceId, batch.bib(), ping.lat(), ping.lon(), ping.recordedAt()))
                .toList();
        events.locationPings(raceId, batch.bib(), pings);
    }
}
