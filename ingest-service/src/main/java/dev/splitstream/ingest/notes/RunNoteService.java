package dev.splitstream.ingest.notes;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Validator;

import org.springframework.stereotype.Service;

@Service
public class RunNoteService {

    private final RunNoteExtractor extractor;
    private final Validator validator;

    RunNoteService(RunNoteExtractor extractor, Validator validator) {
        this.extractor = extractor;
        this.validator = validator;
    }

    /** Model output is treated like any other untrusted input: it passes the same validation or it's rejected. */
    public RunLog parse(String note) {
        RunLog log = extractor.extract(note);

        List<String> problems = new ArrayList<>(validator.validate(log).stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .sorted()
                .toList());
        double distance = log.distanceKm().orElse(Double.MAX_VALUE);
        if (log.niggles() != null && log.niggles().stream().anyMatch(n -> n.startedAtKm().orElse(0.0) > distance)) {
            problems.add("a niggle starts after the run ended");
        }
        if (!problems.isEmpty()) {
            throw new RunNoteUnreadableException("The extracted run log failed validation.", problems);
        }
        return log;
    }
}
