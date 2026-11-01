# 2. Spring Boot for the API, Quarkus for the streaming service

**Status:** accepted

## Context

There are two services with different jobs. ingest-service is a conventional REST + JPA app. race-service is a Kafka Streams app with a small read API, and it is the one that will be scaled out and restarted the most.

## Decision

- **ingest-service:** Spring Boot 4. It has the most mature JPA, validation and Kafka producer story, and it's what most teams already run.
- **race-service:** Quarkus with its Kafka Streams extension. It starts fast, uses little memory, and can be compiled to a GraalVM native image (`./mvnw -pl race-service package -Dnative`).
- The two services share **no code**. Each owns its own copy of the event records, and the JSON on the topic is the contract.

## Consequences

- There's a little duplication in the event records. That's deliberate: a shared jar would couple release cycles, and it gets awkward fast across two frameworks.
- race-service reads as a tolerant reader (unknown fields ignored), so ingest-service can add fields without a coordinated deploy.
- ingest-service's integration test asserts on the raw JSON on the wire, including the absence of Java type headers. Breaking the contract fails a test rather than a consumer in production.
- Two frameworks means two sets of conventions. That's a fair cost for a portfolio project meant to show both, and it would need a real argument in a product team.
