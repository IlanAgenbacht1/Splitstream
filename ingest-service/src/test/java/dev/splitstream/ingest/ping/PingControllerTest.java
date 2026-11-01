package dev.splitstream.ingest.ping;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import dev.splitstream.ingest.events.LocationPingEvent;
import dev.splitstream.ingest.events.PublishFailedException;
import dev.splitstream.ingest.events.RaceEventPublisher;
import dev.splitstream.ingest.race.RaceNotFoundException;
import dev.splitstream.ingest.race.RaceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(PingController.class)
class PingControllerTest {

    private static final UUID RACE_ID = UUID.fromString("6f1c2a7e-0000-4000-8000-000000000001");

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    RaceService races;

    @MockitoBean
    RaceEventPublisher events;

    @Test
    void acceptsBatchAndPublishesOneEventPerPing() {
        assertThat(post("""
                {"bib": "101", "pings": [
                  {"lat": -33.8996, "lon": 18.4006, "recordedAt": "2026-10-29T08:00:00Z"},
                  {"lat": -33.8997, "lon": 18.4005, "recordedAt": "2026-10-29T08:00:01Z"}
                ]}
                """)).hasStatus(HttpStatus.ACCEPTED);

        ArgumentCaptor<List<LocationPingEvent>> published = ArgumentCaptor.captor();
        verify(events).locationPings(eq(RACE_ID), eq("101"), published.capture());
        assertThat(published.getValue())
                .extracting(LocationPingEvent::recordedAt)
                .containsExactly(Instant.parse("2026-10-29T08:00:00Z"), Instant.parse("2026-10-29T08:00:01Z"));
    }

    @Test
    void rejectsCoordinatesOffThePlanet() {
        assertThat(post("""
                {"bib": "101", "pings": [{"lat": 91.0, "lon": 18.4, "recordedAt": "2026-10-29T08:00:00Z"}]}
                """)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(events);
    }

    @Test
    void rejectsBibThatWouldCorruptTheMessageKey() {
        assertThat(post("""
                {"bib": "10:1", "pings": [{"lat": -33.9, "lon": 18.4, "recordedAt": "2026-10-29T08:00:00Z"}]}
                """)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(events);
    }

    @Test
    void rejectsEmptyBatch() {
        assertThat(post("""
                {"bib": "101", "pings": []}
                """)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownRaceIsNotFound() {
        doThrow(new RaceNotFoundException(RACE_ID)).when(races).requireExists(RACE_ID);

        assertThat(post("""
                {"bib": "101", "pings": [{"lat": -33.9, "lon": 18.4, "recordedAt": "2026-10-29T08:00:00Z"}]}
                """)).hasStatus(HttpStatus.NOT_FOUND);
        verifyNoInteractions(events);
    }

    @Test
    void brokerTroubleTellsTheDeviceToRetry() {
        doThrow(new PublishFailedException("runner-pings", new TimeoutException()))
                .when(events).locationPings(any(), any(), any());

        assertThat(post("""
                {"bib": "101", "pings": [{"lat": -33.9, "lon": 18.4, "recordedAt": "2026-10-29T08:00:00Z"}]}
                """))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .headers().hasValue(HttpHeaders.RETRY_AFTER, "1");
    }

    private MockMvcTester.MockMvcRequestBuilder post(String json) {
        return mvc.post().uri("/api/races/{raceId}/pings", RACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
