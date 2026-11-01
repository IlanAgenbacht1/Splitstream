package dev.splitstream.ingest.notes;

import java.util.List;
import java.util.Optional;

import dev.splitstream.ingest.notes.RunLog.Niggle;
import dev.splitstream.ingest.notes.RunLog.RunType;
import dev.splitstream.ingest.notes.RunLog.Severity;
import dev.splitstream.ingest.notes.RunLog.Side;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The model is mocked here. What's under test is how its answers are checked and turned into HTTP. */
@WebMvcTest(RunNoteController.class)
@Import(RunNoteService.class)
class RunNoteControllerTest {

    private static final String NOTE = """
            {"note": "Easy 10k, 58 min. Left knee sore after 7k."}
            """;

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    RunNoteExtractor extractor;

    @Test
    void returnsTheExtractedLog() {
        when(extractor.extract(anyString())).thenReturn(log(10.0, new Niggle("knee", Side.LEFT, Optional.of(7.0), Severity.MILD)));

        assertThat(post(NOTE))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.niggles[0].side").isEqualTo("LEFT");
    }

    @Test
    void rejectsBlankNoteWithoutCallingTheModel() {
        assertThat(post("""
                {"note": "   "}
                """)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(extractor);
    }

    @Test
    void impossibleValuesFromTheModelAreRejected() {
        when(extractor.extract(anyString())).thenReturn(log(900.0));

        assertThat(post(NOTE))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.problems[0]").asString().startsWith("distanceKm");
    }

    @Test
    void niggleAfterTheFinishIsRejected() {
        when(extractor.extract(anyString())).thenReturn(log(5.0, new Niggle("calf", Side.RIGHT, Optional.of(8.0), Severity.MILD)));

        assertThat(post(NOTE))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.problems[0]").isEqualTo("a niggle starts after the run ended");
    }

    @Test
    void modelApiTroubleTellsTheClientToRetry() {
        when(extractor.extract(anyString())).thenThrow(new ExtractionUnavailableException(new RuntimeException()));

        assertThat(post(NOTE))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .headers().hasValue(HttpHeaders.RETRY_AFTER, "10");
    }

    private static RunLog log(double distanceKm, Niggle... niggles) {
        return new RunLog(Optional.of(distanceKm), Optional.of(58), RunType.EASY, Optional.of(3), List.of(niggles),
                "Easy run with a sore knee.");
    }

    private MockMvcTester.MockMvcRequestBuilder post(String json) {
        return mvc.post().uri("/api/run-notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
