package dev.splitstream.ingest;

import org.springframework.boot.SpringApplication;

/** Runs the service against throwaway Testcontainers instead of docker-compose. Handy from the IDE. */
public class TestIngestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(IngestServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
