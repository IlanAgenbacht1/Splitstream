# 5. LLM output is untrusted input

**Status:** accepted

## Context

`POST /api/run-notes` turns a runner's free-text note ("easy 10k, left knee sore after 7") into a structured run log, using an LLM. The model can be wrong in three ways:

1. **Shape:** malformed JSON, missing fields, extra fields.
2. **Values:** a 900 km run, an effort of 14, a niggle that starts after the finish.
3. **Invention:** the note never gave a distance, but the model fills one in anyway.

## Decision

Each failure mode gets its own defence. `RunLog` is the single source for all three.

- **Shape: structured outputs.** The JSON schema is generated from the `RunLog` record and sent with the request, and the API holds the model to it. There's no JSON repair or retry-on-parse-error loop.
- **Invention: nullable in the schema.** Fields a note might leave out are `Optional`, so the schema says `["number", "null"]`. A plain `Double` would become a required number, and constrained decoding would force the model to make one up. `RunLogSchemaTest` guards this.
- **Values: the same Bean Validation as any request body.** The answer is validated with the annotations on `RunLog`, plus one cross-field rule. A failure returns **422** with the list of problems. It's never silently "fixed".

Errors are mapped to HTTP like the rest of the service:

| Situation | Response |
|---|---|
| Note blank or too long | 400, the model is never called |
| Model's answer fails validation, or the model declines | 422 + `problems` |
| Rate limit, overload, network (after the SDK's own retries) | 503 + `Retry-After` |

## Testing

- **`RunNoteControllerTest`:** the model is mocked behind `RunNoteExtractor`. It tests the checks and the HTTP mapping, which is the part we own. Runs in CI.
- **`RunNoteExtractorLiveTest`:** a small eval of notes with known right answers, run against the real model. It needs `ANTHROPIC_API_KEY`, so it's skipped in CI and run by hand when the prompt or model changes.

## Trade-offs

- A 422 pushes the problem back to the caller instead of retrying. Retrying with the same note usually gives the same answer, and silent retries hide prompt problems.
- `Optional` record components are unusual in Java. Here they earn their place, because they control the schema the model is held to.
