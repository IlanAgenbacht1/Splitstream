package dev.splitstream.ingest.race;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int distanceMeters;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Race() {
    }

    public Race(String name, int distanceMeters, Instant startsAt) {
        this.name = name;
        this.distanceMeters = distanceMeters;
        this.startsAt = startsAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDistanceMeters() {
        return distanceMeters;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
