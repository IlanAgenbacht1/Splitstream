package dev.splitstream.ingest.race;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RaceRepository extends JpaRepository<Race, UUID> {
}
