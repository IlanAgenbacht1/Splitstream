package dev.splitstream.ingest.notes;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The note was fine as a request, but no valid run log came out of it. */
class RunNoteUnreadableException extends ErrorResponseException {

    RunNoteUnreadableException(String detail) {
        this(detail, List.of());
    }

    RunNoteUnreadableException(String detail, List<String> problems) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, problem(detail, problems), null);
    }

    private static ProblemDetail problem(String detail, List<String> problems) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, detail);
        if (!problems.isEmpty()) {
            body.setProperty("problems", problems);
        }
        return body;
    }
}
