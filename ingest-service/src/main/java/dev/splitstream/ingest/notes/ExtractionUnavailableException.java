package dev.splitstream.ingest.notes;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The model API is rate limiting, overloaded or unreachable. The SDK has already retried by this point. */
class ExtractionUnavailableException extends ErrorResponseException {

    ExtractionUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "Run notes can't be read right now. Retry shortly."),
                cause);
        getHeaders().set(HttpHeaders.RETRY_AFTER, "10");
    }
}
