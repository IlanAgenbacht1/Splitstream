package dev.splitstream.ingest.notes;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/run-notes")
class RunNoteController {

    private final RunNoteService notes;

    RunNoteController(RunNoteService notes) {
        this.notes = notes;
    }

    @PostMapping
    RunLog parse(@Valid @RequestBody RunNoteRequest request) {
        return notes.parse(request.note());
    }
}
