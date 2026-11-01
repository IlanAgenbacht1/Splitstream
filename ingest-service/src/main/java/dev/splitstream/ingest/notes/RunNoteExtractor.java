package dev.splitstream.ingest.notes;

/** Turns a runner's note into a run log. The result is unchecked model output: validate it before use. */
interface RunNoteExtractor {

    RunLog extract(String note);
}
