package dev.splitstream.ingest.events;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Kafka didn't acknowledge in time. Surfaces as 503 + Retry-After so devices back off and resend. */
public class PublishFailedException extends ErrorResponseException {

    public PublishFailedException(String topic, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "Event could not be published to " + topic + ". Retry shortly."),
                cause);
        getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
    }
}
