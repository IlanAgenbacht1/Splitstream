package dev.splitstream.ingest.race;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class RaceNotFoundException extends ErrorResponseException {

    public RaceNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No race with id " + id), null);
    }
}
